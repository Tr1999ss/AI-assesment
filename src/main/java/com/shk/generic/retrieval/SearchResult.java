package com.shk.generic.retrieval;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * @param hits     best first
 * @param applied  the retrieval settings that actually ran
 * @param warnings stages that were requested but degraded or skipped, with the reason
 * @param explain  per-stage detail; only with {@code explain=true}
 */
public record SearchResult(
        String query,
        List<SearchHit> hits,
        @Nullable AppliedRetrieval applied,
        List<String> warnings,
        @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable RetrievalExplanation explain
) {
    public SearchResult {
        hits = List.copyOf(hits);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public SearchResult(String query, List<SearchHit> hits) {
        this(query, hits, null, List.of(), null);
    }
}
