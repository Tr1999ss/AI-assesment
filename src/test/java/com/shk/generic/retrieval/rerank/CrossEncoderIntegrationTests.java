package com.shk.generic.retrieval.rerank;

import com.shk.generic.AbstractIntegrationTest;
import com.shk.generic.TestTokens;
import com.shk.generic.retrieval.eval.EvaluationService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CROSS_ENCODER rerank through the whole stack, against a local server speaking the Hugging Face TEI
 * {@code /rerank} format (stand-in for a self-hosted bge-reranker). Scores the parental-leave passage highest.
 */
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CrossEncoderIntegrationTests extends AbstractIntegrationTest {

    private static final HttpServer TEI = startTei();

    @DynamicPropertySource
    static void crossEncoder(DynamicPropertyRegistry registry) {
        registry.add("retrieval.rerank.cross-encoder.url", () -> "http://localhost:" + TEI.getAddress().getPort());
    }

    @Autowired
    MockMvcTester mvc;

    @Autowired
    EvaluationService evaluation;

    @BeforeAll
    void seed() {
        evaluation.seedCorpus(evaluation.loadDefault());
    }

    @AfterAll
    void stopServer() {
        TEI.stop(0);
    }

    @Test
    void configuredCrossEncoderIsAdvertisedAndReranks() {
        assertThat(mvc.get().uri("/api/search/modes").with(TestTokens.reader())).bodyJson()
                .extractingPath("$.rerank[?(@.value=='CROSS_ENCODER')].enabled").asArray().containsExactly(true);

        MvcTestResult r = mvc.get().uri("/api/search?q=leave&mode=keyword&categories=hr&rerank=cross_encoder")
                .with(TestTokens.reader()).exchange();

        assertThat(r).hasStatusOk();
        assertThat(r).bodyJson().extractingPath("$.applied.rerank").isEqualTo("CROSS_ENCODER");
        assertThat(r).bodyJson().extractingPath("$.applied.llmCalls").isEqualTo(0);
        assertThat(r).bodyJson().extractingPath("$.hits[0].title").isEqualTo("Parental leave");
        assertThat(r).bodyJson().extractingPath("$.hits[0].score").isEqualTo(0.99);
        assertThat(r).bodyJson().extractingPath("$.hits[0].scoreType").isEqualTo("RERANK");
    }

    private static HttpServer startTei() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/rerank", exchange -> {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                String texts = body.substring(body.indexOf("\"texts\":[") + 9);
                Matcher m = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(texts.substring(0, texts.indexOf("]")));
                StringBuilder out = new StringBuilder("[");
                int i = 0;
                while (m.find()) {
                    double score = m.group(1).contains("sixteen weeks") ? 0.99 : 0.01;
                    out.append(i == 0 ? "" : ",").append("{\"index\":").append(i++).append(",\"score\":").append(score).append("}");
                }
                byte[] bytes = out.append("]").toString().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
