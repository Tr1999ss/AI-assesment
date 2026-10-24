package com.shk.generic.retrieval.options;

import org.jspecify.annotations.Nullable;

/**
 * What the caller asked for. Every field is optional; nulls are filled from configuration by the
 * {@link RetrievalPlanner}.
 *
 * @param candidates size of the candidate pool fed to fusion, reranking and MMR
 * @param mmrLambda  MMR trade-off: 1 = pure relevance, 0 = pure diversity
 * @param explain    include per-stage ranks and scores in the response
 */
public record RetrievalOptions(
        @Nullable RetrievalMode mode,
        @Nullable QueryTransformType queryTransform,
        @Nullable RerankerType rerank,
        @Nullable DiversifierType diversify,
        @Nullable Integer candidates,
        @Nullable Double mmrLambda,
        boolean explain
) {
    public static final RetrievalOptions DEFAULTS = new RetrievalOptions(null, null, null, null, null, null, false);

    /** Parses REST string parameters; invalid values raise a 400 listing the valid ones. */
    public static RetrievalOptions parse(@Nullable String mode, @Nullable String queryTransform,
                                         @Nullable String rerank, @Nullable String diversify,
                                         @Nullable Integer candidates, @Nullable Double mmrLambda,
                                         @Nullable Boolean explain) {
        return new RetrievalOptions(
                blank(mode) ? null : RetrievalOption.parse(RetrievalMode.class, "mode", mode),
                blank(queryTransform) ? null : RetrievalOption.parse(QueryTransformType.class, "queryTransform", queryTransform),
                blank(rerank) ? null : RetrievalOption.parse(RerankerType.class, "rerank", rerank),
                blank(diversify) ? null : RetrievalOption.parse(DiversifierType.class, "diversify", diversify),
                candidates, mmrLambda, Boolean.TRUE.equals(explain));
    }

    private static boolean blank(@Nullable String s) {
        return s == null || s.isBlank();
    }
}
