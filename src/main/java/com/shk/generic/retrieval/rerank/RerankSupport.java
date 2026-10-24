package com.shk.generic.retrieval.rerank;

import com.shk.generic.retrieval.pipeline.Candidate;
import com.shk.generic.retrieval.pipeline.ScoreType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Applies per-candidate scores (indexed like the input) and returns the candidates re-sorted. */
final class RerankSupport {

    private RerankSupport() {
    }

    /**
     * @param scores 0..1, same order as {@code candidates}. Ties keep the incoming order (stable sort),
     *               so a reranker that can't tell candidates apart doesn't scramble them.
     */
    static List<Candidate> apply(String stage, List<Candidate> candidates, double[] scores) {
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            order.add(i);
        }
        order.sort(Comparator.comparingDouble((Integer i) -> scores[i]).reversed());
        List<Candidate> out = new ArrayList<>(candidates.size());
        for (int rank = 0; rank < order.size(); rank++) {
            int i = order.get(rank);
            out.add(candidates.get(i).rescored(stage, rank + 1, clamp(scores[i]), ScoreType.RERANK));
        }
        return out;
    }

    static double clamp(double v) {
        return Double.isNaN(v) ? 0.0 : Math.max(0.0, Math.min(1.0, v));
    }
}
