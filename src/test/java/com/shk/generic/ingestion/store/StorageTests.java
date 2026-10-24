package com.shk.generic.ingestion.store;

import com.shk.generic.AbstractIntegrationTest;
import com.shk.generic.TestTokens;
import com.shk.generic.ingestion.IngestionResult;
import com.shk.generic.ingestion.IngestionService;
import com.shk.generic.ingestion.IngestionStatus;
import com.shk.generic.ingestion.chunk.ChunkMetadata;
import com.shk.generic.ingestion.intake.IngestionRequest;
import com.shk.generic.ingestion.intake.IntakeChannel;
import com.shk.generic.ingestion.intake.NamedByteArrayResource;
import com.shk.generic.ingestion.intake.SourceFormat;
import com.shk.generic.ingestion.registry.DocumentRegistry;
import com.shk.generic.ingestion.registry.RegisteredDocument;
import com.shk.generic.ingestion.registry.RegistrationStatus;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@AutoConfigureMockMvc
class StorageTests extends AbstractIntegrationTest {

    @Autowired
    IngestionService ingestionService;

    @Autowired
    DocumentRegistry registry;

    @Autowired
    VectorStore vectorStore;

    @Autowired
    ChunkStore chunkStore;

    @Autowired
    StalePendingSweeper sweeper;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    MockMvcTester mvc;

    @Test
    void ingestionStoresChunksAndMarksTheDocumentIngested() {
        IngestionResult r = ingestionService.ingest(request("handbook.txt", unique("Annual leave is twenty days."),
                List.of("hr", "legal")));

        assertThat(r.status()).isEqualTo(IngestionStatus.INGESTED);
        assertThat(r.chunkCount()).isPositive();
        assertThat(chunksOf(r.documentId())).isEqualTo(r.chunkCount());

        RegisteredDocument row = registry.findById(UUID.fromString(r.documentId())).orElseThrow();
        assertThat(row.status()).isEqualTo(RegistrationStatus.INGESTED);
        assertThat(row.primaryCategory()).isEqualTo("hr");
        assertThat(row.categories()).containsExactly("hr", "legal");
        assertThat(row.chunkCount()).isEqualTo(r.chunkCount());
    }

    @Test
    void embeddedTextCarriesContextHeaderButNoIdsOrHashes() {
        IngestionResult r = ingestionService.ingest(request("leave.txt", unique("Leave rules."), List.of("hr")));
        RegisteredDocument row = registry.findById(UUID.fromString(r.documentId())).orElseThrow();

        assertThat(embeddingModel.embeddedTexts()).singleElement().satisfies(text -> assertThat(text)
                .startsWith("Document: leave.txt\n\n")
                .doesNotContain(row.contentHash())
                .doesNotContain(r.documentId())
                .doesNotContain("hr"));
    }

    @Test
    void categoryFilterMatchesAnyCategoryInTheArrayNotJustPrimary() {
        String marker = UUID.randomUUID().toString().substring(0, 8);
        IngestionResult legalHr = ingestionService.ingest(
                request("contract.txt", "Contract " + marker + " for employment.", List.of("legal", "hr")));
        IngestionResult finance = ingestionService.ingest(
                request("budget.txt", "Budget " + marker + " for next year.", List.of("finance")));
        String scope = " && document_id IN ['" + legalHr.documentId() + "','" + finance.documentId() + "']";

        assertThat(search(marker, "categories == 'hr'" + scope)).containsOnly(legalHr.documentId());
        assertThat(search(marker, "categories == 'legal'" + scope)).containsOnly(legalHr.documentId());
        assertThat(search(marker, "categories IN ['finance', 'engineering']" + scope)).containsOnly(finance.documentId());
        assertThat(search(marker, "primary_category == 'hr'" + scope)).isEmpty();
        assertThat(search(marker, "categories != 'finance'" + scope)).containsOnly(legalHr.documentId());
    }

    @Test
    void duplicateReportsTheStoredDocumentsCategoriesAndChunkCount() {
        String text = unique("Expense claims are due monthly.");
        IngestionResult first = ingestionService.ingest(request("expenses.txt", text, List.of("finance", "hr")));

        IngestionResult second = ingestionService.ingest(request("copy.txt", text, List.of("legal")));

        assertThat(second.status()).isEqualTo(IngestionStatus.DUPLICATE);
        assertThat(second.primaryCategory()).isEqualTo("finance");
        assertThat(second.categories()).containsExactly("finance", "hr");
        assertThat(second.chunkCount()).isEqualTo(first.chunkCount());
        assertThat(embeddingModel.embeddedTexts()).as("duplicates are not re-embedded").hasSize(first.chunkCount());
    }

    @Test
    void embeddingOutageIs503LeavesNothingBehindAndCanBeRetried() {
        String text = unique("Outage test.");
        embeddingModel.fail(new IllegalStateException("embeddings 429, key=sk-secret"));

        assertThat(mvc.post().uri("/api/ingest/text").with(TestTokens.editor()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"outage\",\"text\":\"" + text + "\",\"categories\":[\"hr\"]}"))
                .hasStatus(503).bodyText().doesNotContain("sk-secret");
        assertThat(rowsWithFilename("outage.txt")).isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM vector_store WHERE content LIKE :t")
                .param("t", "%" + text + "%").query(Integer.class).single()).isZero();

        embeddingModel.reset();
        IngestionResult retry = ingestionService.ingest(request("outage.txt", text, List.of("hr")));
        assertThat(retry.status()).isEqualTo(IngestionStatus.INGESTED);
        assertThat(chunksOf(retry.documentId())).isEqualTo(retry.chunkCount());
    }

    @Test
    void rewritingTheSameChunksDoesNotDuplicateThem() {
        IngestionResult r = ingestionService.ingest(request("idem.txt", unique("Idempotency."), List.of("hr")));
        List<Document> stored = vectorStore.similaritySearch(SearchRequest.builder().query("Idempotency")
                .topK(10).similarityThresholdAll().filterExpression("document_id == '" + r.documentId() + "'").build());

        chunkStore.write(stored.stream().map(d -> new Document(d.getId(), d.getText(), d.getMetadata())).toList());

        assertThat(chunksOf(r.documentId())).isEqualTo(r.chunkCount());
    }

    @Test
    void sweeperReleasesOnlyStaleClaimsAndRemovesTheirChunks() {
        UUID stale = insertPending("2 hours");
        UUID fresh = insertPending("1 minute");
        chunkStore.write(List.of(chunkFor(stale), chunkFor(fresh)));
        IngestionResult ingested = ingestionService.ingest(request("keep.txt", unique("Keep me."), List.of("hr")));
        jdbc.sql("UPDATE document_registry SET created_at = now() - interval '3 hours' WHERE id = :id")
                .param("id", UUID.fromString(ingested.documentId())).update();

        sweeper.sweep();

        assertThat(registry.findById(stale)).isEmpty();
        assertThat(chunksOf(stale.toString())).isZero();
        assertThat(registry.findById(fresh)).isPresent();
        assertThat(chunksOf(fresh.toString())).isOne();
        assertThat(registry.findById(UUID.fromString(ingested.documentId()))).isPresent();
        assertThat(chunksOf(ingested.documentId())).isEqualTo(ingested.chunkCount());
    }

    private List<String> search(String query, String filter) {
        return vectorStore.similaritySearch(SearchRequest.builder().query(query).topK(20)
                        .similarityThresholdAll().filterExpression(filter).build())
                .stream().map(d -> (String) d.getMetadata().get(ChunkMetadata.DOCUMENT_ID)).distinct().toList();
    }

    private int chunksOf(String documentId) {
        return jdbc.sql("SELECT count(*) FROM vector_store WHERE metadata->>'document_id' = :id")
                .param("id", documentId).query(Integer.class).single();
    }

    private int rowsWithFilename(String filename) {
        return jdbc.sql("SELECT count(*) FROM document_registry WHERE filename = :f")
                .param("f", filename).query(Integer.class).single();
    }

    private UUID insertPending(String age) {
        UUID id = UUID.randomUUID();
        String hash = (id.toString().replace("-", "") + id.toString().replace("-", "")).toLowerCase();
        jdbc.sql("""
                        INSERT INTO document_registry (id, content_hash, filename, format, channel, status, created_at)
                        VALUES (:id, :hash, 'orphan.txt', 'TEXT', 'JSON_TEXT', 'PENDING', now() - CAST(:age AS interval))
                        """)
                .param("id", id)
                .param("hash", hash)
                .param("age", age)
                .update();
        return id;
    }

    private static Document chunkFor(UUID documentId) {
        return new Document(UUID.randomUUID().toString(), "orphan chunk",
                Map.of(ChunkMetadata.DOCUMENT_ID, documentId.toString()));
    }

    private static String unique(String text) {
        return text + " Ref " + UUID.randomUUID() + ".";
    }

    private static IngestionRequest request(String filename, String text, List<String> categories) {
        return IngestionRequest.of(IntakeChannel.JSON_TEXT, filename, SourceFormat.TEXT,
                new NamedByteArrayResource(text.getBytes(StandardCharsets.UTF_8), filename), categories, null);
    }
}
