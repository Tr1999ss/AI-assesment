package com.shk.generic.retrieval;

import java.util.List;

/** Hits ordered by score, best first. */
public record SearchResult(String query, List<SearchHit> hits) {

    public SearchResult {
        hits = List.copyOf(hits);
    }
}
