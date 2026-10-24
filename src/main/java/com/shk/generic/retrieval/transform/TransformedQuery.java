package com.shk.generic.retrieval.transform;

import java.util.List;

/**
 * The texts each leg should search. Every (leg, text) pair becomes one ranked list; several lists are
 * fused with RRF.
 *
 * @param semanticQueries texts for the semantic leg (HyDE puts the hypothetical answer here)
 * @param keywordQueries  texts for the keyword leg (always real queries, never generated passages)
 * @param detail          human-readable note for explain output
 */
public record TransformedQuery(List<String> semanticQueries, List<String> keywordQueries, String detail) {

    public TransformedQuery {
        semanticQueries = List.copyOf(semanticQueries);
        keywordQueries = List.copyOf(keywordQueries);
    }

    public static TransformedQuery unchanged(String query) {
        return new TransformedQuery(List.of(query), List.of(query), "query used as written");
    }
}
