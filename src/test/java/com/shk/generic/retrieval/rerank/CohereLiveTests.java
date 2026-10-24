package com.shk.generic.retrieval.rerank;

import com.shk.generic.retrieval.llm.LlmCallBudget;
import com.shk.generic.retrieval.pipeline.Candidate;
import com.shk.generic.retrieval.pipeline.ScoreType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.Duration;
import java.util.List;

import static com.shk.generic.retrieval.RetrievalTestSupport.hit;
import static org.assertj.core.api.Assertions.assertThat;

/** Calls the real Cohere Rerank API. Runs only when COHERE_API_KEY is set (costs a fraction of a cent). */
@EnabledIfEnvironmentVariable(named = "COHERE_API_KEY", matches = ".+",
        disabledReason = "COHERE_API_KEY is not set: live Cohere rerank test skipped")
class CohereLiveTests {

    @Test
    void realCohereRerankPutsTheAnsweringPassageFirst() {
        CohereReranker cohere = new CohereReranker(new RerankProperties(Duration.ofSeconds(15),
                new RerankProperties.Llm(10, 600),
                new RerankProperties.Cohere(System.getenv("COHERE_API_KEY"), "rerank-v3.5", "https://api.cohere.com"),
                new RerankProperties.CrossEncoder("", "")));
        List<Candidate> pool = List.of(
                Candidate.first(hit("a", "Hotel rates are capped at 180 EUR per night."), "t", 1, 0.9, ScoreType.COSINE),
                Candidate.first(hit("b", "Birth parents receive sixteen weeks of fully paid parental leave."), "t", 2, 0.8, ScoreType.COSINE),
                Candidate.first(hit("c", "Invoices are payable within fourteen days."), "t", 3, 0.7, ScoreType.COSINE));

        List<Candidate> out = cohere.rerank("How long is maternity leave?", pool, new LlmCallBudget(0));

        assertThat(out.get(0).chunkId()).isEqualTo("b");
        assertThat(out.get(0).score()).isBetween(0.0, 1.0);
    }
}
