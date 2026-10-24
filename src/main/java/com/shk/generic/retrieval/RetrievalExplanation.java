package com.shk.generic.retrieval;

import java.util.List;

/**
 * Per-stage detail returned with {@code explain=true}. Per-chunk ranks and scores are in each hit's {@code trace}.
 *
 * @param semanticQueries texts the semantic leg searched (after rewriting / multi-query / HyDE)
 * @param keywordQueries  texts the keyword leg searched
 */
public record RetrievalExplanation(
        String originalQuery,
        List<String> semanticQueries,
        List<String> keywordQueries,
        List<Stage> stages
) {
    public RetrievalExplanation {
        semanticQueries = List.copyOf(semanticQueries);
        keywordQueries = List.copyOf(keywordQueries);
        stages = List.copyOf(stages);
    }

    /**
     * @param name        e.g. {@code transform:HYDE}, {@code semantic[0]}, {@code rrf}, {@code rerank:LLM}, {@code mmr}
     * @param candidates  number of chunks the stage output
     * @param durationMs  wall time of the stage
     */
    public record Stage(String name, int candidates, long durationMs, String detail) {
    }
}
