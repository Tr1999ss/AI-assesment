package com.shk.generic.retrieval.fusion;

import com.shk.generic.retrieval.pipeline.Candidate;
import com.shk.generic.retrieval.pipeline.RankedList;
import com.shk.generic.retrieval.pipeline.ScoreType;
import com.shk.generic.retrieval.pipeline.StageScore;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.shk.generic.retrieval.RetrievalTestSupport.hit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class ReciprocalRankFusionTest {

    private final ReciprocalRankFusion rrf = new ReciprocalRankFusion(60);

    @Test
    void scoreIsSumOfReciprocalRanks() {
        List<Candidate> fused = rrf.fuse(List.of(
                list("semantic[0]", ScoreType.COSINE, "a", "b", "c"),
                list("keyword[0]", ScoreType.FTS_RANK, "c", "a")));

        // a: 1/61 + 1/62, c: 1/63 + 1/61, b: 1/62
        assertThat(fused).extracting(Candidate::chunkId).containsExactly("a", "c", "b");
        assertThat(fused.get(0).score()).isCloseTo(1.0 / 61 + 1.0 / 62, within(1e-12));
        assertThat(fused.get(1).score()).isCloseTo(1.0 / 63 + 1.0 / 61, within(1e-12));
        assertThat(fused.get(2).score()).isCloseTo(1.0 / 62, within(1e-12));
        assertThat(fused).allSatisfy(c -> assertThat(c.scoreType()).isEqualTo(ScoreType.RRF));
    }

    @Test
    void appearingInMoreListsBeatsOneHighRank() {
        List<Candidate> fused = rrf.fuse(List.of(
                list("l1", ScoreType.COSINE, "solo", "both"),
                list("l2", ScoreType.COSINE, "x", "both"),
                list("l3", ScoreType.COSINE, "y", "both")));

        assertThat(fused.get(0).chunkId()).isEqualTo("both");
    }

    @Test
    void tiesBreakOnBestSingleRankThenFirstAppearance() {
        List<Candidate> fused = rrf.fuse(List.of(
                list("l1", ScoreType.COSINE, "p", "q"),
                list("l2", ScoreType.COSINE, "q", "p")));

        // equal sums and equal best ranks -> first appearance (p) wins, deterministically
        assertThat(fused).extracting(Candidate::chunkId).containsExactly("p", "q");
    }

    @Test
    void traceRecordsEveryListAndTheFusedRank() {
        Candidate a = rrf.fuse(List.of(
                list("semantic[0]", ScoreType.COSINE, "a"),
                list("keyword[0]", ScoreType.FTS_RANK, "z", "a"))).stream()
                .filter(c -> c.chunkId().equals("a")).findFirst().orElseThrow();

        assertThat(a.trace()).extracting(StageScore::stage).containsExactly("semantic[0]", "keyword[0]", "rrf");
        assertThat(a.trace()).extracting(StageScore::rank).containsExactly(1, 2, 1);
    }

    @Test
    void largerKFlattensRankDifferences() {
        double gapSmallK = gap(new ReciprocalRankFusion(1));
        double gapLargeK = gap(new ReciprocalRankFusion(1000));

        assertThat(gapSmallK).isGreaterThan(gapLargeK);
    }

    @Test
    void kMustBePositive() {
        assertThatThrownBy(() -> new ReciprocalRankFusion(0)).isInstanceOf(IllegalArgumentException.class);
    }

    private static double gap(ReciprocalRankFusion fusion) {
        List<Candidate> fused = fusion.fuse(List.of(list("l", ScoreType.COSINE, "first", "second")));
        return fused.get(0).score() - fused.get(1).score();
    }

    private static RankedList list(String source, ScoreType type, String... ids) {
        List<Candidate> candidates = new java.util.ArrayList<>();
        for (int i = 0; i < ids.length; i++) {
            candidates.add(Candidate.first(hit(ids[i], ids[i]), source, i + 1, 1.0 - i * 0.1, type));
        }
        return new RankedList(source, candidates);
    }
}
