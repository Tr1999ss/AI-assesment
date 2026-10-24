package com.shk.generic.retrieval.diversify;

import com.shk.generic.retrieval.options.DiversifierType;
import com.shk.generic.retrieval.pipeline.Candidate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Maximal Marginal Relevance (Carbonell & Goldstein, 1998). Greedily picks
 * {@code argmax λ·rel(d) − (1−λ)·max_{s∈selected} sim(d, s)}.
 * <ul>
 *   <li>rel: the upstream stage's score, min-max normalized to 0..1 over the pool, so MMR respects whatever
 *       ranking came before (cosine, RRF or reranker) without mixing score scales</li>
 *   <li>sim: cosine between the chunks' stored embeddings ({@link ChunkSimilarity})</li>
 * </ul>
 * Ties go to the candidate ranked higher upstream. λ = 1 reproduces the input order.
 */
@Component
public class MmrDiversifier implements Diversifier {

    public static final String STAGE = "mmr";

    private final ChunkSimilarity similarity;

    public MmrDiversifier(ChunkSimilarity similarity) {
        this.similarity = similarity;
    }

    @Override
    public DiversifierType type() {
        return DiversifierType.MMR;
    }

    @Override
    public List<Candidate> select(List<Candidate> ranked, int k, double lambda) {
        if (ranked.isEmpty()) {
            return ranked;
        }
        double[] relevance = normalize(ranked);
        SimilarityMatrix sim = similarity.pairwise(ranked.stream().map(Candidate::chunkId).toList());

        List<Integer> remaining = new ArrayList<>();
        for (int i = 0; i < ranked.size(); i++) {
            remaining.add(i);
        }
        List<Integer> selected = new ArrayList<>();
        while (selected.size() < k && !remaining.isEmpty()) {
            int best = -1;
            double bestScore = Double.NEGATIVE_INFINITY;
            for (int i : remaining) {
                double redundancy = 0.0;
                for (int s : selected) {
                    redundancy = Math.max(redundancy, sim.get(ranked.get(i).chunkId(), ranked.get(s).chunkId()));
                }
                double mmr = lambda * relevance[i] - (1 - lambda) * redundancy;
                if (mmr > bestScore) {   // strict: earlier (higher-ranked) candidate wins ties
                    bestScore = mmr;
                    best = i;
                }
            }
            selected.add(best);
            remaining.remove(Integer.valueOf(best));
        }
        List<Candidate> out = new ArrayList<>(selected.size());
        for (int rank = 0; rank < selected.size(); rank++) {
            out.add(ranked.get(selected.get(rank)).reranked(STAGE, rank + 1));
        }
        return out;
    }

    static double[] normalize(List<Candidate> ranked) {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (Candidate c : ranked) {
            min = Math.min(min, c.score());
            max = Math.max(max, c.score());
        }
        double[] out = new double[ranked.size()];
        for (int i = 0; i < ranked.size(); i++) {
            out[i] = max > min ? (ranked.get(i).score() - min) / (max - min) : 1.0;
        }
        return out;
    }
}
