package com.shk.generic.retrieval.keyword;

import com.shk.generic.AbstractIntegrationTest;
import com.shk.generic.TestTokens;
import com.shk.generic.retrieval.eval.EvaluationService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * True BM25 keyword retrieval on the ParadeDB image (pgvector + pg_search), configured exactly like the
 * 'paradedb' profile: engine flag plus the optional migration location.
 */
@SpringBootTest(properties = {
        "spring.ai.openai.api-key=test-key",
        "ingestion.store.sweeper-enabled=false",
        "retrieval.keyword.engine=paradedb-bm25",
        "spring.flyway.locations=classpath:db/migration,classpath:db/migration-paradedb"
})
@Import({ParadeDbBm25Tests.ParadeDbContainer.class, AbstractIntegrationTest.StubModelsConfiguration.class})
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ParadeDbBm25Tests {

    @TestConfiguration(proxyBeanMethods = false)
    static class ParadeDbContainer {
        @Bean
        @ServiceConnection
        PostgreSQLContainer paradedb() {
            return new PostgreSQLContainer(DockerImageName.parse("paradedb/paradedb:0.21.0-pg16")
                    .asCompatibleSubstituteFor("postgres"));
        }
    }

    @Autowired
    MockMvcTester mvc;

    @Autowired
    EvaluationService evaluation;

    @BeforeAll
    void seed() {
        evaluation.seedCorpus(evaluation.loadDefault());
    }

    @Test
    void bm25FindsExactCodesAndReportsItsEngineAndScoreType() {
        MvcTestResult r = search("q=ERR-4012&mode=keyword");

        assertThat(r).hasStatusOk();
        assertThat(r).bodyJson().extractingPath("$.applied.keywordEngine").isEqualTo("PARADEDB_BM25");
        assertThat(r).bodyJson().extractingPath("$.hits[0].scoreType").isEqualTo("BM25");
        assertThat(r).bodyJson().extractingPath("$.hits[0].source").isEqualTo("payments-runbook.md");
        assertThat(r).bodyJson().extractingPath("$.hits[0].title").isEqualTo("Error codes");
    }

    @Test
    void bm25MatchesNaturalQuestionsAndAppliesTheSameFilters() {
        assertThat(search("q=How many weeks of parental leave do new parents get?&mode=keyword"))
                .bodyJson().extractingPath("$.hits[0].title").isEqualTo("Parental leave");
        assertThat(search("q=receipts&mode=keyword&categories=legal"))
                .bodyJson().extractingPath("$.hits").asArray().isEmpty();
        assertThat(search("q=receipts&mode=keyword&categories=finance&scope=PRIMARY&topK=20"))
                .bodyJson().extractingPath("$.hits[*].primaryCategory").asArray().isNotEmpty().containsOnly("finance");
    }

    @Test
    void userInputIsNeverParsedAsQuerySyntax() {
        assertThat(search("q=\"unbalanced -- ERR-4012 content:( AND OR&mode=keyword")).hasStatusOk();
    }

    @Test
    void hybridFusesBm25WithSemantic() {
        MvcTestResult r = search("q=ERR-5003 ledger&mode=hybrid&explain=true");

        assertThat(r).hasStatusOk();
        assertThat(r).bodyJson().extractingPath("$.applied.scoreType").isEqualTo("RRF");
        assertThat(r).bodyJson().extractingPath("$.hits[0].trace[*].scoreType").asArray().contains("BM25", "COSINE", "RRF");
    }

    private MvcTestResult search(String query) {
        return mvc.get().uri("/api/search?" + query).with(TestTokens.reader()).exchange();
    }
}
