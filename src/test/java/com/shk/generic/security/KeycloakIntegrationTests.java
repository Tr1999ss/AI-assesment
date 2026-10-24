package com.shk.generic.security;

import com.shk.generic.AbstractIntegrationTest;
import com.shk.generic.TestcontainersConfiguration;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * End to end with a real Keycloak (same realm file as compose.yaml) and a real HTTP server:
 * token issuance, signature/issuer/audience validation, role mapping, auditing, and MCP over SSE.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.ai.openai.api-key=test-key",
        "ingestion.store.sweeper-enabled=false"
})
@Import({TestcontainersConfiguration.class, AbstractIntegrationTest.StubModelsConfiguration.class})
class KeycloakIntegrationTests {

    @Container
    static final GenericContainer<?> KEYCLOAK = new GenericContainer<>("quay.io/keycloak/keycloak:26.4")
            .withCommand("start-dev", "--import-realm")
            .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", "admin")
            .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", "admin")
            .withCopyFileToContainer(MountableFile.forHostPath("docker/keycloak/generic-realm.json"),
                    "/opt/keycloak/data/import/generic-realm.json")
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/realms/generic/.well-known/openid-configuration").forPort(8080)
                    .withStartupTimeout(Duration.ofMinutes(3)));

    @DynamicPropertySource
    static void keycloak(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", KeycloakIntegrationTests::issuer);
    }

    private final HttpClient http = HttpClient.newHttpClient();
    private final JsonMapper json = JsonMapper.shared();

    @LocalServerPort
    int port;

    @Autowired
    JdbcClient jdbc;

    @Test
    void realTokensAreValidatedAndRolesEnforced() throws Exception {
        String alice = token("generic-cli", "alice");
        String bob = token("generic-cli", "bob");

        assertThat(get("/api/search?q=anything", alice).statusCode()).isEqualTo(200);
        assertThat(ingest(alice).statusCode()).isEqualTo(403);
        assertThat(get("/api/admin/categories", bob).statusCode()).isEqualTo(403);
        assertThat(get("/api/admin/categories", token("generic-cli", "carol")).statusCode()).isEqualTo(200);
    }

    @Test
    void ingestionIsAuditedWithTheKeycloakUsername() throws Exception {
        HttpResponse<String> r = ingest(token("generic-cli", "bob"));

        assertThat(r.statusCode()).isEqualTo(200);
        String documentId = json.readTree(r.body()).get("documentId").asString();
        assertThat(jdbc.sql("SELECT ingested_by FROM document_registry WHERE id = :id")
                .param("id", UUID.fromString(documentId)).query(String.class).single()).isEqualTo("bob");
    }

    @Test
    void invalidTokensAreRejected() throws Exception {
        assertThat(get("/api/search?q=x", null).statusCode()).isEqualTo(401);
        assertThat(get("/api/search?q=x", "not-a-jwt").statusCode()).isEqualTo(401);

        // Valid signature and issuer, but issued to Keycloak's admin-cli: no generic-api audience.
        HttpResponse<String> wrongAudience = get("/api/search?q=x", token("admin-cli", "carol"));
        assertThat(wrongAudience.statusCode()).isEqualTo(401);
        assertThat(wrongAudience.headers().firstValue("WWW-Authenticate")).hasValueSatisfying(
                v -> assertThat(v).contains("invalid_token"));
    }

    @Test
    void mcpOverSseEnforcesPerToolRoles() throws Exception {
        try (McpSyncClient asAlice = mcp(token("generic-cli", "alice"));
             McpSyncClient asBob = mcp(token("generic-cli", "bob"))) {

            assertThat(asAlice.listTools().tools()).extracting(Tool::name)
                    .contains("search_knowledge_base", "ask_knowledge_base", "list_categories", "ingest_document");
            assertThat(asAlice.callTool(new CallToolRequest("search_knowledge_base", Map.of("query", "x"))).isError())
                    .isNotEqualTo(Boolean.TRUE);

            Map<String, Object> doc = Map.of("name", "mcp.txt", "text", "MCP e2e " + UUID.randomUUID());
            assertThat(deniedOrError(() -> asAlice.callTool(new CallToolRequest("ingest_document", doc))))
                    .as("reader must not ingest over MCP").isTrue();
            assertThat(asBob.callTool(new CallToolRequest("ingest_document", doc)).isError())
                    .isNotEqualTo(Boolean.TRUE);
        }
    }

    @Test
    void mcpRetrievalToolsExposeEnumeratedOptionsAndEnforceRoles() throws Exception {
        try (McpSyncClient asAlice = mcp(token("generic-cli", "alice"))) {
            Tool search = asAlice.listTools().tools().stream()
                    .filter(t -> t.name().equals("search_knowledge_base")).findFirst().orElseThrow();
            String schema = String.valueOf(search.inputSchema().get("properties"));
            assertThat(schema).contains("SEMANTIC", "KEYWORD", "HYBRID", "MULTI_QUERY", "HYDE", "CROSS_ENCODER", "MMR");

            CallToolResult hybrid = asAlice.callTool(new CallToolRequest("search_knowledge_base",
                    Map.of("query", "leave", "mode", "HYBRID", "diversify", "MMR", "explain", true)));
            assertThat(hybrid.isError()).isNotEqualTo(Boolean.TRUE);
            assertThat(text(hybrid)).contains("\"mode\":\"HYBRID\"", "\"diversify\":\"MMR\"");

            assertThat(asAlice.callTool(new CallToolRequest("list_retrieval_options", Map.of())).isError())
                    .isNotEqualTo(Boolean.TRUE);

            // unavailable or invalid options: recoverable tool errors, not dropped connections
            CallToolResult cohere = asAlice.callTool(new CallToolRequest("search_knowledge_base",
                    Map.of("query", "leave", "rerank", "COHERE")));
            assertThat(cohere.isError()).isTrue();
            assertThat(text(cohere)).contains("COHERE_API_KEY is not set");
            assertThat(deniedOrError(() -> asAlice.callTool(new CallToolRequest("search_knowledge_base",
                    Map.of("query", "leave", "mode", "FUZZY"))))).isTrue();
            assertThat(asAlice.callTool(new CallToolRequest("search_knowledge_base", Map.of("query", "still works"))).isError())
                    .as("session usable after errors").isNotEqualTo(Boolean.TRUE);
        }

        assertThat(get("/api/search/modes", token("generic-cli", "alice")).statusCode()).isEqualTo(200);
        String dave = token("generic-cli", "dave");
        assertThat(get("/api/search/modes", dave).statusCode()).isEqualTo(403);
        assertThat(catchThrowable(() -> mcp(dave))).as("no kb role: MCP connection refused").isNotNull();
    }

    private static String text(CallToolResult result) {
        return result.content().stream()
                .filter(c -> c instanceof io.modelcontextprotocol.spec.McpSchema.TextContent)
                .map(c -> ((io.modelcontextprotocol.spec.McpSchema.TextContent) c).text())
                .reduce("", String::concat);
    }

    private McpSyncClient mcp(String token) {
        var transport = HttpClientSseClientTransport.builder("http://localhost:" + port)
                .requestBuilder(HttpRequest.newBuilder().header("Authorization", "Bearer " + token))
                .build();
        McpSyncClient client = McpClient.sync(transport).requestTimeout(Duration.ofSeconds(30)).build();
        client.initialize();
        return client;
    }

    /** The server may report a denied tool call as an error result or as a JSON-RPC error. */
    private static boolean deniedOrError(java.util.function.Supplier<CallToolResult> call) {
        CallToolResult[] result = new CallToolResult[1];
        Throwable thrown = catchThrowable(() -> result[0] = call.get());
        return thrown != null || Boolean.TRUE.equals(result[0].isError());
    }

    private String token(String clientId, String user) throws IOException, InterruptedException {
        String form = "grant_type=password&client_id=" + clientId + "&username=" + user + "&password="
                + URLEncoder.encode(user, StandardCharsets.UTF_8);
        HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(issuer() + "/protocol/openid-connect/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(r.statusCode()).as("token for %s via %s: %s", user, clientId, r.body()).isEqualTo(200);
        return json.readTree(r.body()).get("access_token").asString();
    }

    private HttpResponse<String> get(String path, String token) throws IOException, InterruptedException {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (token != null) {
            b.header("Authorization", "Bearer " + token);
        }
        return http.send(b.GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> ingest(String token) throws IOException, InterruptedException {
        String body = "{\"title\":\"kc\",\"text\":\"Keycloak test " + UUID.randomUUID() + "\",\"categories\":[\"general\"]}";
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/ingest/text"))
                .header("Authorization", "Bearer " + token).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String issuer() {
        return "http://" + KEYCLOAK.getHost() + ":" + KEYCLOAK.getMappedPort(8080) + "/realms/generic";
    }
}
