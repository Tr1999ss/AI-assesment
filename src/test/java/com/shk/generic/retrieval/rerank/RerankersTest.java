package com.shk.generic.retrieval.rerank;

import com.shk.generic.StubChatModel;
import com.shk.generic.retrieval.RetrievalTestSupport;
import com.shk.generic.retrieval.llm.LlmCallBudget;
import com.shk.generic.retrieval.pipeline.Candidate;
import com.shk.generic.retrieval.pipeline.ScoreType;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.shk.generic.retrieval.RetrievalTestSupport.candidate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Rerankers against a stub chat model and a local HTTP server standing in for Cohere / TEI. */
class RerankersTest {

    private HttpServer server;
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private final AtomicReference<String> authHeader = new AtomicReference<>();

    private final List<Candidate> pool = List.of(candidate("a", 0.9), candidate("b", 0.8), candidate("c", 0.7));

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void llmRerankerScoresInBatchesAndReorders() {
        StubChatModel model = new StubChatModel();
        // batch size 2: batch 1 = [a, b], batch 2 = [c]
        model.when("[1] text of a", "{\"scores\":[{\"id\":1,\"score\":2},{\"id\":2,\"score\":9}]}");
        model.when("[1] text of c", "{\"scores\":[{\"id\":1,\"score\":6}]}");
        LlmReranker reranker = new LlmReranker(ChatClient.builder(model),
                RetrievalTestSupport.llmExecutor(Duration.ofSeconds(5)), props(2, "", ""));
        LlmCallBudget budget = new LlmCallBudget(6);

        List<Candidate> out = reranker.rerank("query", pool, budget);

        assertThat(out).extracting(Candidate::chunkId).containsExactly("b", "c", "a");
        assertThat(out).extracting(Candidate::score).containsExactly(0.9, 0.6, 0.2);
        assertThat(out).allSatisfy(c -> assertThat(c.scoreType()).isEqualTo(ScoreType.RERANK));
        assertThat(budget.used()).isEqualTo(2);
        assertThat(reranker.llmCalls(3)).isEqualTo(2);
    }

    @Test
    void llmRerankerGivesUnscoredPassagesZeroAndKeepsTiesStable() {
        StubChatModel model = new StubChatModel();
        model.reply("{\"scores\":[{\"id\":3,\"score\":8},{\"id\":99,\"score\":10}]}");
        LlmReranker reranker = new LlmReranker(ChatClient.builder(model),
                RetrievalTestSupport.llmExecutor(Duration.ofSeconds(5)), props(10, "", ""));

        assertThat(reranker.rerank("q", pool, new LlmCallBudget(6))).extracting(Candidate::chunkId)
                .containsExactly("c", "a", "b");
    }

    @Test
    void cohereSendsKeyAndDocumentsAndMapsRelevanceScores() throws IOException {
        start("/v2/rerank", 200, """
                {"results":[{"index":2,"relevance_score":0.91},{"index":0,"relevance_score":0.40},{"index":1,"relevance_score":0.05}]}""");
        CohereReranker cohere = new CohereReranker(props(10, "test-cohere-key", ""));

        List<Candidate> out = cohere.rerank("expense receipts", pool, new LlmCallBudget(0));

        assertThat(out).extracting(Candidate::chunkId).containsExactly("c", "a", "b");
        assertThat(authHeader.get()).isEqualTo("Bearer test-cohere-key");
        assertThat(requestBody.get()).contains("\"model\":\"rerank-v3.5\"", "\"query\":\"expense receipts\"",
                "\"top_n\":3", "text of a");
        assertThat(cohere.unavailableReason()).isEmpty();
    }

    @Test
    void cohereErrorsExposeStatusOnlyNeverBodyOrKey() throws IOException {
        start("/v2/rerank", 401, "{\"message\":\"invalid api token test-cohere-key\"}");
        CohereReranker cohere = new CohereReranker(props(10, "test-cohere-key", ""));

        assertThatThrownBy(() -> cohere.rerank("q", pool, new LlmCallBudget(0)))
                .isInstanceOf(RerankException.class)
                .hasMessage("cohere: HTTP 401");
    }

    @Test
    void cohereIsUnavailableWithoutKeyAndKeyIsMaskedInToString() {
        assertThat(new CohereReranker(props(10, "", "")).unavailableReason()).contains("COHERE_API_KEY is not set");
        assertThat(props(10, "secret-key", "").cohere().toString()).doesNotContain("secret-key").contains("***");
    }

    @Test
    void crossEncoderSpeaksTeiRerankFormat() throws IOException {
        start("/rerank", 200, "[{\"index\":1,\"score\":0.97},{\"index\":0,\"score\":0.02},{\"index\":2,\"score\":0.5}]");
        CrossEncoderReranker tei = new CrossEncoderReranker(props(10, "", "http://localhost:" + server.getAddress().getPort()));

        List<Candidate> out = tei.rerank("q", pool, new LlmCallBudget(0));

        assertThat(out).extracting(Candidate::chunkId).containsExactly("b", "c", "a");
        assertThat(requestBody.get()).contains("\"texts\":[", "\"raw_scores\":false", "\"truncate\":true");
        assertThat(authHeader.get()).isNull();
    }

    @Test
    void crossEncoderIsUnavailableWithoutUrl() {
        CrossEncoderReranker tei = new CrossEncoderReranker(props(10, "", ""));

        assertThat(tei.unavailableReason()).contains("CROSS_ENCODER_URL is not set");
        assertThatThrownBy(() -> tei.rerank("q", pool, new LlmCallBudget(0))).isInstanceOf(RerankException.class);
    }

    private RerankProperties props(int batch, String cohereKey, String crossEncoderUrl) {
        String cohereBase = server == null ? "https://api.cohere.com" : "http://localhost:" + server.getAddress().getPort();
        return new RerankProperties(Duration.ofSeconds(5), new RerankProperties.Llm(batch, 600),
                new RerankProperties.Cohere(cohereKey, "rerank-v3.5", cohereBase),
                new RerankProperties.CrossEncoder(crossEncoderUrl, ""));
    }

    private void start(String path, int status, String response) throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext(path, exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            authHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }
}
