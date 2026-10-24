package com.shk.generic.ingestion.category;

import com.shk.generic.category.CategoryDefinition;
import com.shk.generic.category.CategoryRegistry;
import com.shk.generic.AbstractIntegrationTest;
import com.shk.generic.ingestion.IngestionResult;
import com.shk.generic.ingestion.IngestionService;
import com.shk.generic.ingestion.IngestionStatus;
import com.shk.generic.ingestion.intake.IngestionRequest;
import com.shk.generic.ingestion.intake.IntakeChannel;
import com.shk.generic.ingestion.intake.NamedByteArrayResource;
import com.shk.generic.ingestion.intake.SourceFormat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@AutoConfigureMockMvc
class CategorizationTests extends AbstractIntegrationTest {

    @Autowired
    IngestionService ingestionService;

    @Autowired
    CategoryRegistry categoryRegistry;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    MockMvcTester mvc;

    @Test
    void migrationSeedsActiveCategoriesIncludingFallback() {
        assertThat(categoryRegistry.active()).extracting(CategoryDefinition::name)
                .contains("hr", "finance", "legal", "engineering", "general");
    }

    @Test
    void suppliedCategoriesSkipTheModelAndFirstIsPrimary() {
        IngestionResult r = ingestionService.ingest(request(unique(), List.of("legal", "hr")));

        assertThat(r.status()).isEqualTo(IngestionStatus.INGESTED);
        assertThat(r.primaryCategory()).isEqualTo("legal");
        assertThat(r.categories()).containsExactly("legal", "hr");
        assertThat(r.chunkCount()).isEqualTo(1);
        assertThat(chatModel.prompts()).isEmpty();
    }

    @Test
    void jsonArrayOrderSurvivesTheHttpLayer() {
        String body = """
                {"title":"order","text":"%s","categories":["finance","hr","legal"]}""".formatted(unique());

        MvcTestResult result = mvc.post().uri("/api/ingest/text")
                .contentType(MediaType.APPLICATION_JSON).content(body).exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.primaryCategory").isEqualTo("finance");
        assertThat(result).bodyJson().extractingPath("$.categories").asArray()
                .containsExactly("finance", "hr", "legal");
    }

    @Test
    void unknownSuppliedCategoryIs400AndTheContentCanBeRetried() {
        String text = unique();
        String bad = """
                {"title":"bad","text":"%s","categories":["hr","payroll"]}""".formatted(text);

        assertThat(mvc.post().uri("/api/ingest/text").contentType(MediaType.APPLICATION_JSON).content(bad))
                .hasStatus(400).bodyText().contains("payroll").contains("general");
        assertThat(rowsWithFilename("bad.txt")).isZero();

        IngestionResult retry = ingestionService.ingest(request(text, List.of("hr")));
        assertThat(retry.status()).isEqualTo(IngestionStatus.INGESTED);
    }

    @Test
    void withoutCategoriesTheClassifierDecides() {
        chatModel.reply("""
                {"primary":"legal","primaryConfidence":0.88,
                 "overlaps":[{"name":"finance","confidence":0.75},{"name":"made-up","confidence":0.99}]}""");

        IngestionResult r = ingestionService.ingest(request(unique(), List.of()));

        assertThat(r.primaryCategory()).isEqualTo("legal");
        assertThat(r.categories()).containsExactly("legal", "finance");
        assertThat(chatModel.prompts()).hasSize(1);
        assertThat(chatModel.prompts().get(0).getUserMessage().getText())
                .contains("- engineering: Software and systems");
    }

    @Test
    void unconfidentClassificationFallsBackToGeneral() {
        chatModel.reply("""
                {"primary":"hr","primaryConfidence":0.2,"overlaps":[]}""");

        IngestionResult r = ingestionService.ingest(request(unique(), List.of()));

        assertThat(r.primaryCategory()).isEqualTo("general");
        assertThat(r.categories()).containsExactly("general");
    }

    @Test
    void classifierOutageIs503ReleasesTheClaimAndTheContentCanBeRetried() {
        String text = unique();
        chatModel.fail(new IllegalStateException("upstream timeout, key=sk-secret"));

        assertThat(mvc.post().uri("/api/ingest/text").contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"outage\",\"text\":\"" + text + "\"}"))
                .hasStatus(503).bodyText().doesNotContain("sk-secret");
        assertThat(rowsWithFilename("outage.txt")).isZero();

        chatModel.reset();
        IngestionResult retry = ingestionService.ingest(request(text, List.of()));
        assertThat(retry.status()).isEqualTo(IngestionStatus.INGESTED);
        assertThat(retry.primaryCategory()).isEqualTo("general");
    }

    private static String unique() {
        return "Document " + UUID.randomUUID() + " about something.";
    }

    private static IngestionRequest request(String text, List<String> categories) {
        String filename = UUID.randomUUID() + ".txt";
        return IngestionRequest.of(IntakeChannel.JSON_TEXT, filename, SourceFormat.TEXT,
                new NamedByteArrayResource(text.getBytes(StandardCharsets.UTF_8), filename), categories, null);
    }

    private int rowsWithFilename(String filename) {
        return jdbc.sql("SELECT count(*) FROM document_registry WHERE filename = :f")
                .param("f", filename).query(Integer.class).single();
    }
}
