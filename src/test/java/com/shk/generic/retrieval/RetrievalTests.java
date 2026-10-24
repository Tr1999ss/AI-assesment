package com.shk.generic.retrieval;

import com.shk.generic.AbstractIntegrationTest;
import com.shk.generic.TestTokens;
import com.shk.generic.category.admin.CategoryAdminService;
import com.shk.generic.ingestion.IngestionResult;
import com.shk.generic.ingestion.IngestionService;
import com.shk.generic.ingestion.intake.IngestionRequest;
import com.shk.generic.ingestion.intake.IntakeChannel;
import com.shk.generic.ingestion.intake.NamedByteArrayResource;
import com.shk.generic.ingestion.intake.SourceFormat;
import com.shk.generic.retrieval.mcp.RetrievalMcpTools;
import com.shk.generic.security.Roles;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Two dedicated categories keep these documents apart from everything else in the shared test database:
 * leave policy tagged [people, contracts], expense policy tagged [contracts].
 */
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RetrievalTests extends AbstractIntegrationTest {

    private final String people = "people-" + UUID.randomUUID().toString().substring(0, 6);
    private final String contracts = "contracts-" + UUID.randomUUID().toString().substring(0, 6);

    @Autowired
    MockMvcTester mvc;

    @Autowired
    IngestionService ingestionService;

    @Autowired
    CategoryAdminService categoryAdmin;

    @Autowired
    RetrievalMcpTools mcpTools;

    @Autowired
    JdbcClient jdbc;

    private IngestionResult leave;
    private IngestionResult expenses;

    @BeforeAll
    void ingestFixtures() {
        categoryAdmin.create(people, "People topics.");
        categoryAdmin.create(contracts, "Contract topics.");
        leave = ingestionService.ingest(request("leave.txt",
                "Annual leave policy: employees receive twenty vacation days per year.", List.of(people, contracts)));
        expenses = ingestionService.ingest(request("expenses.txt",
                "Expense policy: submit receipts within thirty days of purchase.", List.of(contracts)));
    }

    @AfterAll
    void cleanUp() {
        for (IngestionResult r : List.of(leave, expenses)) {
            jdbc.sql("DELETE FROM vector_store WHERE metadata->>'document_id' = :id").param("id", r.documentId()).update();
            jdbc.sql("DELETE FROM document_registry WHERE id = :id").param("id", UUID.fromString(r.documentId())).update();
        }
        categoryAdmin.delete(people);
        categoryAdmin.delete(contracts);
    }

    @Test
    void searchRanksTheMatchingChunkFirstWithItsMetadata() {
        MvcTestResult r = search("q=vacation days per year&categories=" + contracts + "&minScore=0");

        assertThat(r).hasStatusOk();
        assertThat(r).bodyJson().extractingPath("$.hits.length()").isEqualTo(2);
        assertThat(r).bodyJson().extractingPath("$.hits[0].documentId").isEqualTo(leave.documentId());
        assertThat(r).bodyJson().extractingPath("$.hits[0].source").isEqualTo("leave.txt");
        assertThat(r).bodyJson().extractingPath("$.hits[0].primaryCategory").isEqualTo(people);
        assertThat(r).bodyJson().extractingPath("$.hits[0].categories").asArray().containsExactly(people, contracts);
    }

    @Test
    void anyScopeMatchesOverlapTagsPrimaryScopeOnlyThePrimary() {
        assertThat(search("q=policy&categories=" + people + "&minScore=0"))
                .bodyJson().extractingPath("$.hits[*].documentId").asArray().containsExactly(leave.documentId());
        assertThat(search("q=policy&categories=" + contracts + "&scope=PRIMARY&minScore=0"))
                .bodyJson().extractingPath("$.hits[*].documentId").asArray().containsExactly(expenses.documentId());
    }

    @Test
    void invalidRequestsAre400() {
        assertThat(search("q=  ")).hasStatus(400);
        assertThat(search("q=x&topK=0")).hasStatus(400);
        assertThat(search("q=x&topK=21")).hasStatus(400);
        assertThat(search("q=x&minScore=1.5")).hasStatus(400);
        assertThat(search("q=x&categories=no-such-category")).hasStatus(400)
                .bodyText().contains("no-such-category");
    }

    @Test
    void askAnswersFromSourcesWithCitations() {
        chatModel.reply("Employees receive twenty vacation days per year [1].");

        MvcTestResult r = mvc.post().uri("/api/ask").with(TestTokens.reader()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"Annual leave policy: how many vacation days per year do employees receive?\",\"categories\":[\"" + people + "\"]}").exchange();

        assertThat(r).hasStatusOk();
        assertThat(r).bodyJson().extractingPath("$.grounded").isEqualTo(true);
        assertThat(r).bodyJson().extractingPath("$.citations[0].documentId").isEqualTo(leave.documentId());
        assertThat(r).bodyJson().extractingPath("$.citations[0].source").isEqualTo("leave.txt");
        assertThat(chatModel.prompts().get(0).getUserMessage().getText()).contains("[1] leave.txt");
    }

    @Test
    void askModelOutageIs503WithoutLeakingDetails() {
        chatModel.fail(new IllegalStateException("401 key=sk-secret"));

        assertThat(mvc.post().uri("/api/ask").with(TestTokens.reader()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"Annual leave policy: how many vacation days per year do employees receive?\",\"categories\":[\"" + people + "\"]}"))
                .hasStatus(503).bodyText().doesNotContain("sk-secret");
    }

    @Test
    @WithMockUser(roles = Roles.READER)
    void mcpToolsUseTheSameServices() {
        assertThat(mcpTools.listCategories()).extracting("name").contains(people, contracts, "general");
        assertThat(mcpTools.search("expense policy: submit receipts within thirty days", List.of(contracts), true, 5, null, null, null, null, null, null).hits())
                .extracting(SearchHit::documentId).containsExactly(expenses.documentId());
    }

    private MvcTestResult search(String query) {
        return mvc.get().uri("/api/search?" + query).with(TestTokens.reader()).exchange();
    }

    private static IngestionRequest request(String filename, String text, List<String> categories) {
        String unique = text + " Ref " + UUID.randomUUID();
        return IngestionRequest.of(IntakeChannel.JSON_TEXT, filename, SourceFormat.TEXT,
                new NamedByteArrayResource(unique.getBytes(StandardCharsets.UTF_8), filename), categories, null);
    }
}
