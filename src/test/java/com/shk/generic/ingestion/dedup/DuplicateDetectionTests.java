package com.shk.generic.ingestion.dedup;

import com.shk.generic.AbstractIntegrationTest;
import com.shk.generic.ingestion.IngestionResult;
import com.shk.generic.ingestion.IngestionService;
import com.shk.generic.ingestion.IngestionStatus;
import com.shk.generic.ingestion.intake.IngestionRequest;
import com.shk.generic.ingestion.intake.IntakeChannel;
import com.shk.generic.ingestion.intake.NamedByteArrayResource;
import com.shk.generic.ingestion.intake.SourceFormat;
import com.shk.generic.ingestion.registry.DocumentRegistry;
import com.shk.generic.ingestion.registry.RegisteredDocument;
import com.shk.generic.ingestion.registry.RegistrationStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@AutoConfigureMockMvc
class DuplicateDetectionTests extends AbstractIntegrationTest {

    @Autowired
    IngestionService ingestionService;

    @Autowired
    DocumentRegistry registry;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    MockMvcTester mvc;

    @Test
    void contentHashHasDatabaseLevelUniqueConstraint() {
        String hash = Sha256ContentHasher.sha256(UUID.randomUUID().toString());
        insertRaw(hash);

        assertThatThrownBy(() -> insertRaw(hash))
                .isInstanceOf(DuplicateKeyException.class)
                .hasMessageContaining("uq_document_registry_content_hash");
    }

    @Test
    void secondUploadOfSameContentReturnsExistingDocumentId() {
        String text = uniqueText();

        IngestionResult first = ingestionService.ingest(textRequest("first.txt", text, Set.of()));
        IngestionResult second = ingestionService.ingest(textRequest("second.txt", text, Set.of()));

        assertThat(first.status()).isEqualTo(IngestionStatus.INGESTED);
        assertThat(first.chunkCount()).isPositive();
        assertThat(second.status()).isEqualTo(IngestionStatus.DUPLICATE);
        assertThat(second.chunkCount()).as("reports the existing document").isEqualTo(first.chunkCount());
        assertThat(second.documentId()).isEqualTo(first.documentId());
        assertThat(second.filename()).isEqualTo("first.txt");
        assertThat(rowsWithHashOf(text)).isEqualTo(1);
    }

    @Test
    void whitespaceAndLineEndingVariantsAreDuplicates() {
        String token = UUID.randomUUID().toString();

        IngestionResult lf = ingestionService.ingest(textRequest("a.txt", token + "  one\ntwo", Set.of()));
        IngestionResult crlf = ingestionService.ingest(textRequest("b.txt", token + " one\r\n\r\ntwo  ", Set.of()));

        assertThat(crlf.status()).isEqualTo(IngestionStatus.DUPLICATE);
        assertThat(crlf.documentId()).isEqualTo(lf.documentId());
    }

    @Test
    void duplicateWithDifferentCategoriesLeavesExistingDocumentUnchanged() {
        String text = uniqueText();
        IngestionResult first = ingestionService.ingest(textRequest("policy.txt", text, Set.of("hr")));
        RegisteredDocument before = registry.findById(UUID.fromString(first.documentId())).orElseThrow();

        IngestionResult second = ingestionService.ingest(textRequest("policy-v2.txt", text, Set.of("finance", "legal")));

        assertThat(second.status()).isEqualTo(IngestionStatus.DUPLICATE);
        assertThat(second.documentId()).isEqualTo(first.documentId());
        assertThat(second.categories()).containsExactly("hr");
        RegisteredDocument after = registry.findById(before.id()).orElseThrow();
        assertThat(after).isEqualTo(before);
        assertThat(after.categories()).containsExactly("hr");
        assertThat(after.filename()).isEqualTo("policy.txt");
    }

    @Test
    void concurrentIdenticalUploadsCreateExactlyOneDocument() throws Exception {
        int threads = 16;
        String text = uniqueText();
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<IngestionResult>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                IngestionRequest request = textRequest("copy-" + i + ".txt", text, Set.of(List.of("hr", "finance", "legal").get(i % 3)));
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return ingestionService.ingest(request);
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();

            List<IngestionResult> results = new ArrayList<>();
            for (Future<IngestionResult> f : futures) {
                results.add(f.get(30, TimeUnit.SECONDS));   // any exception fails the test
            }

            assertThat(results).filteredOn(r -> r.status() == IngestionStatus.INGESTED).hasSize(1);
            assertThat(results).filteredOn(r -> r.status() == IngestionStatus.DUPLICATE).hasSize(threads - 1);
            assertThat(results).extracting(IngestionResult::documentId).containsOnly(results.get(0).documentId());
            assertThat(rowsWithHashOf(text)).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void duplicateUploadOverHttpIsOkNotAnError() {
        byte[] body = uniqueText().getBytes(StandardCharsets.UTF_8);
        var file = new MockMultipartFile("file", "notes.md", "text/markdown", body);

        assertThat(mvc.post().uri("/api/ingest/file").multipart().file(file))
                .hasStatusOk().bodyJson().extractingPath("$.status").isEqualTo("INGESTED");
        assertThat(mvc.post().uri("/api/ingest/file").multipart().file(file).param("categories", "other"))
                .hasStatusOk().bodyJson().extractingPath("$.status").isEqualTo("DUPLICATE");
    }

    @Test
    void extractionFailuresDoNotRegisterAnything() {
        long before = jdbc.sql("SELECT count(*) FROM document_registry").query(Long.class).single();

        assertThat(mvc.post().uri("/api/ingest/text").contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"x\",\"text\":\"%PDF-garbage\",\"format\":\"PDF\"}"))
                .hasStatus(422);

        assertThat(jdbc.sql("SELECT count(*) FROM document_registry").query(Long.class).single()).isEqualTo(before);
    }

    private static String uniqueText() {
        return "Leave policy " + UUID.randomUUID() + ": twenty days per year.";
    }

    private static IngestionRequest textRequest(String filename, String text, Set<String> categories) {
        return IngestionRequest.of(IntakeChannel.JSON_TEXT, filename, SourceFormat.TEXT,
                new NamedByteArrayResource(text.getBytes(StandardCharsets.UTF_8), filename), categories, null);
    }

    private int rowsWithHashOf(String text) {
        String normalized = text.replaceAll("\\s+", " ").strip();
        return jdbc.sql("SELECT count(*) FROM document_registry WHERE content_hash = :h")
                .param("h", Sha256ContentHasher.sha256(normalized))
                .query(Integer.class).single();
    }

    private void insertRaw(String hash) {
        jdbc.sql("""
                        INSERT INTO document_registry (id, content_hash, filename, format, channel, status)
                        VALUES (:id, :hash, 'raw.txt', 'TEXT', 'JSON_TEXT', :status)
                        """)
                .param("id", UUID.randomUUID())
                .param("hash", hash)
                .param("status", RegistrationStatus.PENDING.name())
                .update();
    }
}
