package com.shk.generic.security;

import com.shk.generic.AbstractIntegrationTest;
import com.shk.generic.TestTokens;
import com.shk.generic.ingestion.intake.mcp.IngestionMcpTools;
import com.shk.generic.retrieval.mcp.RetrievalMcpTools;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Who may call what. Mock tokens here; {@code KeycloakIntegrationTests} proves real Keycloak tokens. */
@AutoConfigureMockMvc
class SecurityTests extends AbstractIntegrationTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    IngestionMcpTools ingestionTools;

    @Autowired
    RetrievalMcpTools retrievalTools;

    @Test
    void anonymousCallersGet401AsProblemDetailWithBearerChallenge() {
        MvcTestResult r = mvc.get().uri("/api/search?q=x").exchange();

        assertThat(r).hasStatus(401).headers().hasHeaderSatisfying("WWW-Authenticate",
                values -> assertThat(values.get(0)).startsWith("Bearer"));
        assertThat(r).bodyJson().extractingPath("$.status").isEqualTo(401);
        assertThat(mvc.post().uri("/api/ingest/text").contentType(MediaType.APPLICATION_JSON).content(textBody()))
                .hasStatus(401);
        assertThat(mvc.get().uri("/sse")).hasStatus(401);
    }

    @Test
    void publicEndpointsNeedNoToken() {
        assertThat(mvc.get().uri("/api/categories")).hasStatusOk();
        assertThat(mvc.get().uri("/actuator/health")).hasStatusOk()
                .bodyJson().doesNotHavePath("$.components");
    }

    @Test
    void readerCanSearchButNotIngestOrAdminister() {
        assertThat(mvc.get().uri("/api/search?q=anything").with(TestTokens.reader())).hasStatusOk();
        assertThat(ingest(TestTokens.reader())).hasStatus(403).bodyJson().extractingPath("$.status").isEqualTo(403);
        assertThat(mvc.get().uri("/api/admin/categories").with(TestTokens.reader())).hasStatus(403);
    }

    @Test
    void editorCanIngestAndSearchButNotAdminister() {
        assertThat(ingest(TestTokens.editor())).hasStatusOk();
        assertThat(mvc.get().uri("/api/search?q=anything").with(TestTokens.editor())).hasStatusOk();
        assertThat(mvc.get().uri("/api/admin/categories").with(TestTokens.editor())).hasStatus(403);
    }

    @Test
    void adminCanDoEverything() {
        assertThat(mvc.get().uri("/api/admin/categories").with(TestTokens.admin())).hasStatusOk();
        assertThat(ingest(TestTokens.admin())).hasStatusOk();
        assertThat(mvc.get().uri("/api/search?q=anything").with(TestTokens.admin())).hasStatusOk();
    }

    @Test
    void tokenWithoutKnowledgeBaseRolesIsForbidden() {
        assertThat(mvc.get().uri("/api/search?q=x").with(TestTokens.user("mallory", "offline_access")))
                .hasStatus(403);
    }

    @Test
    void ingestionRecordsWhoSubmittedTheDocument() throws Exception {
        MvcTestResult r = ingest(TestTokens.user("dana", Roles.EDITOR));
        assertThat(r).hasStatusOk();
        String documentId = r.getResponse().getContentAsString().replaceAll(".*\"documentId\":\"([^\"]+)\".*", "$1");

        assertThat(jdbc.sql("SELECT ingested_by FROM document_registry WHERE id = :id")
                .param("id", UUID.fromString(documentId)).query(String.class).single()).isEqualTo("dana");
    }

    @Test
    @WithMockUser(username = "alice", roles = Roles.READER)
    void mcpIngestToolRejectsReaders() {
        assertThatThrownBy(() -> ingestionTools.ingestDocument("x.txt", UUID.randomUUID().toString(), null, null, null))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(retrievalTools.search("anything", null, null, null, null, null, null, null, null, null)).isNotNull();
    }

    @Test
    @WithMockUser(username = "bob", roles = Roles.EDITOR)
    void mcpIngestToolAllowsEditors() {
        assertThat(ingestionTools.ingestDocument("mcp.txt", "MCP " + UUID.randomUUID(), null, null, null).documentId())
                .isNotNull();
    }

    @Test
    void mcpToolsRefuseCallsWithNoAuthenticationAtAll() {
        assertThatThrownBy(() -> retrievalTools.search("x", null, null, null, null, null, null, null, null, null))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    private MvcTestResult ingest(RequestPostProcessor token) {
        return mvc.post().uri("/api/ingest/text").with(token).contentType(MediaType.APPLICATION_JSON)
                .content(textBody()).exchange();
    }

    private static String textBody() {
        return "{\"title\":\"sec\",\"text\":\"Security test " + UUID.randomUUID() + "\",\"categories\":[\"general\"]}";
    }
}
