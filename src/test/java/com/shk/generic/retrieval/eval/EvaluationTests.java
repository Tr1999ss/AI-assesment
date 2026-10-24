package com.shk.generic.retrieval.eval;

import com.shk.generic.AbstractIntegrationTest;
import com.shk.generic.TestTokens;
import com.shk.generic.retrieval.keyword.ParadeDbBm25Engine;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * The evaluation runner end to end with stub models: checks the mechanics (seeding, metrics, baseline deltas,
 * access control). Stub embeddings are bag-of-words, so these numbers say nothing about real quality;
 * see LiveEvaluationTests for real ones.
 */
@AutoConfigureMockMvc
class EvaluationTests extends AbstractIntegrationTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    JsonMapper json;

    @Test
    void reportsEveryConfigurationAgainstTheBaseline() throws Exception {
        MvcTestResult r = evaluate(TestTokens.admin(), """
                {"seedCorpus":true,"k":5,"details":true,"configurations":["semantic","keyword","hybrid","hybrid+mmr"]}""");

        assertThat(r).hasStatusOk();
        JsonNode report = json.readTree(r.getResponse().getContentAsString());
        assertThat(report.get("baseline").asString()).isEqualTo("semantic");
        assertThat(report.get("questions").asInt()).isEqualTo(26);
        JsonNode results = report.get("results");
        assertThat(results).hasSize(4);
        assertThat(results.get(0).get("delta").isNull()).as("baseline has no delta").isTrue();

        JsonNode baseline = results.get(0);
        for (int i = 1; i < results.size(); i++) {
            JsonNode res = results.get(i);
            assertThat(res.get("delta").get("hitRate").asDouble())
                    .isCloseTo(res.get("hitRate").asDouble() - baseline.get("hitRate").asDouble(), within(1e-9));
            assertThat(res.get("delta").get("mrr").asDouble())
                    .isCloseTo(res.get("mrr").asDouble() - baseline.get("mrr").asDouble(), within(1e-9));
        }

        // exact-term questions must be found lexically
        JsonNode keyword = results.get(1);
        for (JsonNode q : keyword.get("perQuestion")) {
            String id = q.get("id").asString();
            if (id.equals("q02") || id.equals("q06") || id.equals("q08") || id.equals("q25")) {
                assertThat(q.get("hit").asDouble()).as("keyword %s", id).isEqualTo(1.0);
            }
        }
    }

    @Test
    void onlyAdminsMayRunEvaluationsAndSpecsAreValidated() {
        assertThat(evaluate(TestTokens.editor(), "{\"configurations\":[\"semantic\"]}")).hasStatus(403);
        assertThat(evaluate(TestTokens.admin(), "{\"configurations\":[\"semantic+warp\"]}")).hasStatus(400)
                .bodyText().contains("Format: <mode>");
    }

    @Test
    void bm25EngineRefusesToStartWithoutPgSearch() {
        assertThatThrownBy(() -> new ParadeDbBm25Engine(jdbc, json).verify())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pg_search")
                .hasMessageContaining("paradedb");
    }

    private MvcTestResult evaluate(org.springframework.test.web.servlet.request.RequestPostProcessor token, String body) {
        return mvc.post().uri("/api/admin/evaluation").with(token).contentType(MediaType.APPLICATION_JSON)
                .content(body).exchange();
    }
}
