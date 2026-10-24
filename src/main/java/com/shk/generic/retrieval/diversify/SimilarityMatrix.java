package com.shk.generic.retrieval.diversify;

import java.util.HashMap;
import java.util.Map;

/** Symmetric chunk-to-chunk similarity lookup. */
public final class SimilarityMatrix {

    private final Map<String, Double> pairs = new HashMap<>();

    public void put(String a, String b, double similarity) {
        pairs.put(key(a, b), similarity);
    }

    public double get(String a, String b) {
        if (a.equals(b)) {
            return 1.0;
        }
        return pairs.getOrDefault(key(a, b), 0.0);
    }

    private static String key(String a, String b) {
        return a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a;
    }
}
