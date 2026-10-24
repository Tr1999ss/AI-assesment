package com.shk.generic.retrieval.diversify;

import com.shk.generic.retrieval.pipeline.Candidate;
import com.shk.generic.retrieval.pipeline.StageScore;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.shk.generic.retrieval.RetrievalTestSupport.candidate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class MmrDiversifierTest {

    /** a and a2 are near-duplicates; b is distinct. */
    private final ChunkSimilarity similarity = ids -> {
        SimilarityMatrix m = new SimilarityMatrix();
        m.put("a", "a2", 0.98);
        m.put("a", "b", 0.10);
        m.put("a2", "b", 0.12);
        return m;
    };

    private final MmrDiversifier mmr = new MmrDiversifier(similarity);

    private final List<Candidate> ranked = List.of(candidate("a", 0.90), candidate("a2", 0.88), candidate("b", 0.70));

    @Test
    void nearDuplicateIsPushedBelowDistinctChunk() {
        // λ=0.3: a2 = 0.3·0.9 − 0.7·0.98 = −0.416, b = 0.3·0 − 0.7·0.10 = −0.07 → b
        List<Candidate> picked = mmr.select(ranked, 2, 0.3);

        assertThat(picked).extracting(Candidate::chunkId).containsExactly("a", "b");
    }

    @Test
    void lambdaOneKeepsUpstreamOrder() {
        assertThat(mmr.select(ranked, 3, 1.0)).extracting(Candidate::chunkId).containsExactly("a", "a2", "b");
    }

    @Test
    void keepsUpstreamScoreAndRecordsMmrRank() {
        Candidate second = mmr.select(ranked, 2, 0.3).get(1);

        assertThat(second.score()).isEqualTo(0.70);
        assertThat(second.trace()).last().extracting(StageScore::stage, StageScore::rank).containsExactly("mmr", 2);
    }

    @Test
    void returnsAtMostKAndHandlesEmptyPools() {
        assertThat(mmr.select(ranked, 1, 0.5)).hasSize(1);
        assertThat(mmr.select(List.of(), 5, 0.5)).isEmpty();
        assertThat(mmr.select(ranked, 10, 0.5)).hasSize(3);
    }

    @Test
    void relevanceIsMinMaxNormalized() {
        double[] rel = MmrDiversifier.normalize(ranked);

        assertThat(rel).containsExactly(new double[]{1.0, 0.9, 0.0}, within(1e-9));
        assertThat(MmrDiversifier.normalize(List.of(candidate("x", 0.4), candidate("y", 0.4)))).containsExactly(1.0, 1.0);
    }
}
