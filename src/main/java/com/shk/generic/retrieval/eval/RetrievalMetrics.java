package com.shk.generic.retrieval.eval;

import com.shk.generic.retrieval.SearchHit;

import java.util.List;
import java.util.Objects;

/**
 * Standard ranked-retrieval metrics over the top {@code k} hits:
 * <ul>
 *   <li>hit@k: 1 if any relevant item appears in the top k</li>
 *   <li>recall@k: fraction of the relevant items that appear in the top k</li>
 *   <li>reciprocal rank: 1 / rank of the first relevant hit within the top k (0 if none); averaged = MRR@k</li>
 * </ul>
 */
public final class RetrievalMetrics {

    private RetrievalMetrics() {
    }

    public static boolean matches(SearchHit hit, GoldenSet.RelevantItem item) {
        return hit.source().equals(item.source()) && (item.section() == null || Objects.equals(hit.title(), item.section()));
    }

    public static double hitAtK(List<SearchHit> hits, List<GoldenSet.RelevantItem> relevant, int k) {
        return reciprocalRank(hits, relevant, k) > 0 ? 1.0 : 0.0;
    }

    public static double recallAtK(List<SearchHit> hits, List<GoldenSet.RelevantItem> relevant, int k) {
        if (relevant.isEmpty()) {
            return 0.0;
        }
        List<SearchHit> top = hits.subList(0, Math.min(k, hits.size()));
        long found = relevant.stream().filter(item -> top.stream().anyMatch(h -> matches(h, item))).count();
        return (double) found / relevant.size();
    }

    public static double reciprocalRank(List<SearchHit> hits, List<GoldenSet.RelevantItem> relevant, int k) {
        for (int i = 0; i < Math.min(k, hits.size()); i++) {
            SearchHit hit = hits.get(i);
            if (relevant.stream().anyMatch(item -> matches(hit, item))) {
                return 1.0 / (i + 1);
            }
        }
        return 0.0;
    }

    /** Nearest-rank percentile of already-collected values. */
    public static long percentile(List<Long> values, double p) {
        if (values.isEmpty()) {
            return 0;
        }
        List<Long> sorted = values.stream().sorted().toList();
        int index = (int) Math.ceil(p / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }
}
