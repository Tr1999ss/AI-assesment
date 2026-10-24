package com.shk.generic.retrieval.fusion;

import com.shk.generic.retrieval.pipeline.Candidate;
import com.shk.generic.retrieval.pipeline.RankedList;
import com.shk.generic.retrieval.pipeline.ScoreType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reciprocal Rank Fusion (Cormack et al., 2009): {@code score(d) = Σ 1 / (k + rank_i(d))} over every list
 * containing d. Uses only ranks, so it needs no score calibration between cosine, FTS and BM25.
 * Ties are broken by best single rank, then by first appearance, so output is deterministic.
 */
@Component
public class ReciprocalRankFusion implements RankFusion {

    public static final String STAGE = "rrf";

    private final int k;

    @Autowired
    public ReciprocalRankFusion(FusionProperties properties) {
        this(properties.rrfK());
    }

    public ReciprocalRankFusion(int k) {
        if (k < 1) {
            throw new IllegalArgumentException("RRF k must be >= 1");
        }
        this.k = k;
    }

    public int k() {
        return k;
    }

    @Override
    public List<Candidate> fuse(List<RankedList> lists) {
        Map<String, Acc> byChunk = new LinkedHashMap<>();
        for (RankedList list : lists) {
            List<Candidate> candidates = list.candidates();
            for (int i = 0; i < candidates.size(); i++) {
                int rank = i + 1;
                Candidate c = candidates.get(i);
                Acc acc = byChunk.computeIfAbsent(c.chunkId(), id -> new Acc(c, byChunk.size()));
                acc.score += 1.0 / (k + rank);
                acc.bestRank = Math.min(acc.bestRank, rank);
                if (acc.candidate != c) {
                    acc.candidate = acc.candidate.withTraceOf(c);
                }
            }
        }
        List<Acc> ordered = new ArrayList<>(byChunk.values());
        ordered.sort(Comparator.comparingDouble((Acc a) -> a.score).reversed()
                .thenComparingInt(a -> a.bestRank)
                .thenComparingInt(a -> a.order));
        List<Candidate> fused = new ArrayList<>(ordered.size());
        for (int i = 0; i < ordered.size(); i++) {
            Acc a = ordered.get(i);
            fused.add(a.candidate.rescored(STAGE, i + 1, a.score, ScoreType.RRF));
        }
        return fused;
    }

    private static final class Acc {
        Candidate candidate;
        final int order;
        double score;
        int bestRank = Integer.MAX_VALUE;

        Acc(Candidate candidate, int order) {
            this.candidate = candidate;
            this.order = order;
        }
    }
}
