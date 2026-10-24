package com.shk.generic.retrieval.options;

import com.shk.generic.retrieval.CategoryScope;
import com.shk.generic.retrieval.RetrievalProperties;
import com.shk.generic.retrieval.RetrievalTestSupport;
import com.shk.generic.retrieval.SearchQuery;
import com.shk.generic.retrieval.diversify.MmrProperties;
import com.shk.generic.retrieval.keyword.KeywordProperties;
import com.shk.generic.retrieval.llm.LlmCallBudget;
import com.shk.generic.retrieval.pipeline.Candidate;
import com.shk.generic.retrieval.rerank.Reranker;
import com.shk.generic.retrieval.rerank.RerankerRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetrievalPlannerTest {

    private final RetrievalPlanner planner = new RetrievalPlanner(
            new RetrievalProperties(5, 20, 0.25, 2000, 12000, 0.2),
            RetrievalTestSupport.defaults(6, Duration.ofSeconds(5)),
            new KeywordProperties(KeywordProperties.KeywordEngineType.POSTGRES_FTS, 0.0),
            new MmrProperties(0.7),
            new RerankerRegistry(List.of(
                    reranker(RerankerType.NONE, null, 0),
                    reranker(RerankerType.LLM, null, 10),
                    reranker(RerankerType.COHERE, "COHERE_API_KEY is not set", 0),
                    reranker(RerankerType.CROSS_ENCODER, "CROSS_ENCODER_URL is not set", 0))));

    @Test
    void defaultsArePlainSemanticWithPoolEqualToTopK() {
        RetrievalPlan plan = planner.plan(query(RetrievalOptions.DEFAULTS, null));

        assertThat(plan.mode()).isEqualTo(RetrievalMode.SEMANTIC);
        assertThat(plan.queryTransform()).isEqualTo(QueryTransformType.NONE);
        assertThat(plan.rerank()).isEqualTo(RerankerType.NONE);
        assertThat(plan.diversify()).isEqualTo(DiversifierType.NONE);
        assertThat(plan.topK()).isEqualTo(5);
        assertThat(plan.candidates()).isEqualTo(5);
        assertThat(plan.minScore()).isEqualTo(0.25);
        assertThat(plan.estimatedLlmCalls()).isZero();
    }

    @Test
    void anythingThatMergesOrReordersGetsALargerPool() {
        assertThat(planner.plan(query(options(RetrievalMode.HYBRID, null, null, null, null), null)).candidates()).isEqualTo(30);
        assertThat(planner.plan(query(options(null, null, null, DiversifierType.MMR, null), null)).candidates()).isEqualTo(30);
        assertThat(planner.plan(query(options(null, QueryTransformType.MULTI_QUERY, null, null, null), null)).candidates()).isEqualTo(30);
        assertThat(planner.plan(query(options(null, QueryTransformType.HYDE, null, null, null), null)).candidates()).isEqualTo(5);
        assertThat(planner.plan(query(options(RetrievalMode.HYBRID, null, null, null, 12), null)).candidates()).isEqualTo(12);
    }

    @Test
    void candidatesMustBeBetweenTopKAndTheHardLimit() {
        assertThatThrownBy(() -> planner.plan(query(options(null, null, null, null, 3), null)))
                .isInstanceOf(InvalidRetrievalOptionException.class).hasMessageContaining("between topK (5) and 100");
        assertThatThrownBy(() -> planner.plan(query(options(null, null, null, null, 101), null)))
                .isInstanceOf(InvalidRetrievalOptionException.class);
    }

    @Test
    void unconfiguredRerankerIsRejectedWithWhatIsAvailable() {
        assertThatThrownBy(() -> planner.plan(query(options(null, null, RerankerType.COHERE, null, null), null)))
                .isInstanceOf(InvalidRetrievalOptionException.class)
                .hasMessageContaining("COHERE_API_KEY is not set")
                .hasMessageContaining("[NONE, LLM]");
    }

    @Test
    void llmBudgetIsCheckedBeforeAnyWork() {
        // LLM rerank over 30 candidates in batches of 10 = 3 calls + HyDE 1 = 4 <= 6
        RetrievalPlan ok = planner.plan(query(options(RetrievalMode.HYBRID, QueryTransformType.HYDE, RerankerType.LLM, null, null), null));
        assertThat(ok.estimatedLlmCalls()).isEqualTo(4);

        // 70 candidates = 7 calls + 1 > 6
        assertThatThrownBy(() -> planner.plan(query(options(RetrievalMode.HYBRID, QueryTransformType.HYDE, RerankerType.LLM, null, 70), null)))
                .isInstanceOf(InvalidRetrievalOptionException.class)
                .hasMessageContaining("needs 8 LLM calls; the limit is 6");
    }

    @Test
    void rangesAreValidated() {
        assertThatThrownBy(() -> planner.plan(query(RetrievalOptions.DEFAULTS, 21))).hasMessageContaining("topK");
        assertThatThrownBy(() -> planner.plan(new SearchQuery("q", Set.of(), CategoryScope.ANY, null, 1.5)))
                .hasMessageContaining("minScore");
        assertThatThrownBy(() -> planner.plan(query(new RetrievalOptions(null, null, null, DiversifierType.MMR, null, 1.2, false), null)))
                .hasMessageContaining("mmrLambda");
    }

    @Test
    void restValuesParseCaseInsensitivelyAndListValidValuesOnError() {
        RetrievalOptions o = RetrievalOptions.parse("Hybrid", "multi-query", "llm", "mmr", null, null, true);
        assertThat(o.mode()).isEqualTo(RetrievalMode.HYBRID);
        assertThat(o.queryTransform()).isEqualTo(QueryTransformType.MULTI_QUERY);
        assertThat(o.explain()).isTrue();

        assertThatThrownBy(() -> RetrievalOptions.parse("fuzzy", null, null, null, null, null, null))
                .isInstanceOf(InvalidRetrievalOptionException.class)
                .hasMessage("Invalid mode 'fuzzy'; valid values: [SEMANTIC, KEYWORD, HYBRID]")
                .satisfies(e -> assertThat(((InvalidRetrievalOptionException) e).validValues())
                        .containsExactly("SEMANTIC", "KEYWORD", "HYBRID"));
        assertThat(RetrievalOptions.parse(" ", null, null, null, null, null, null).mode()).isNull();
    }

    private static RetrievalOptions options(RetrievalMode mode, QueryTransformType t, RerankerType r,
                                            DiversifierType d, Integer candidates) {
        return new RetrievalOptions(mode, t, r, d, candidates, null, false);
    }

    private static SearchQuery query(RetrievalOptions options, Integer topK) {
        return new SearchQuery("q", Set.of(), CategoryScope.ANY, topK, null, options);
    }

    private static Reranker reranker(RerankerType type, String unavailable, int batch) {
        return new Reranker() {
            @Override
            public RerankerType type() {
                return type;
            }

            @Override
            public Optional<String> unavailableReason() {
                return Optional.ofNullable(unavailable);
            }

            @Override
            public int llmCalls(int candidates) {
                return batch == 0 ? 0 : (candidates + batch - 1) / batch;
            }

            @Override
            public List<Candidate> rerank(String query, List<Candidate> candidates, LlmCallBudget budget) {
                return candidates;
            }
        };
    }
}
