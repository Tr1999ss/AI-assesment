package com.shk.generic.retrieval.eval;

import com.shk.generic.retrieval.SearchHit;
import com.shk.generic.retrieval.options.DiversifierType;
import com.shk.generic.retrieval.options.InvalidRetrievalOptionException;
import com.shk.generic.retrieval.options.QueryTransformType;
import com.shk.generic.retrieval.options.RerankerType;
import com.shk.generic.retrieval.options.RetrievalMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetrievalMetricsTest {

    private final List<GoldenSet.RelevantItem> relevant = List.of(
            new GoldenSet.RelevantItem("expense-policy.md", "Receipts"),
            new GoldenSet.RelevantItem("travel-policy.md", "Receipts for travel"));

    private final List<SearchHit> hits = List.of(
            hit("pricing-faq.md", "Refunds"),
            hit("travel-policy.md", "Receipts for travel"),
            hit("expense-policy.md", "Approval limits"),
            hit("expense-policy.md", "Receipts"));

    @Test
    void hitRecallAndReciprocalRankRespectK() {
        assertThat(RetrievalMetrics.hitAtK(hits, relevant, 1)).isZero();
        assertThat(RetrievalMetrics.hitAtK(hits, relevant, 2)).isEqualTo(1.0);
        assertThat(RetrievalMetrics.recallAtK(hits, relevant, 3)).isEqualTo(0.5);
        assertThat(RetrievalMetrics.recallAtK(hits, relevant, 4)).isEqualTo(1.0);
        assertThat(RetrievalMetrics.reciprocalRank(hits, relevant, 5)).isEqualTo(0.5);
        assertThat(RetrievalMetrics.reciprocalRank(hits, relevant, 1)).isZero();
    }

    @Test
    void sectionlessItemMatchesAnyChunkOfTheSource() {
        var anySection = List.of(new GoldenSet.RelevantItem("expense-policy.md", null));

        assertThat(RetrievalMetrics.reciprocalRank(hits, anySection, 5)).isEqualTo(1.0 / 3);
    }

    @Test
    void percentileIsNearestRank() {
        List<Long> values = List.of(10L, 20L, 30L, 40L, 100L);

        assertThat(RetrievalMetrics.percentile(values, 50)).isEqualTo(30);
        assertThat(RetrievalMetrics.percentile(values, 95)).isEqualTo(100);
        assertThat(RetrievalMetrics.percentile(List.of(), 50)).isZero();
    }

    @Test
    void configSpecsRoundTrip() {
        EvalConfig c = EvalConfig.parse("Hybrid+HyDE+rerank:LLM+mmr");

        assertThat(c.name()).isEqualTo("hybrid+hyde+rerank:llm+mmr");
        assertThat(c.options().mode()).isEqualTo(RetrievalMode.HYBRID);
        assertThat(c.options().queryTransform()).isEqualTo(QueryTransformType.HYDE);
        assertThat(c.options().rerank()).isEqualTo(RerankerType.LLM);
        assertThat(c.options().diversify()).isEqualTo(DiversifierType.MMR);
        assertThat(EvalConfig.parse("semantic").name()).isEqualTo("semantic");
        assertThatThrownBy(() -> EvalConfig.parse("hybrid+turbo"))
                .isInstanceOf(InvalidRetrievalOptionException.class)
                .hasMessageContaining("Format: <mode>");
    }

    private static SearchHit hit(String source, String title) {
        return new SearchHit("c-" + source + title, "d", source, title, null, List.of(), null, 0, 0.5, "t");
    }
}
