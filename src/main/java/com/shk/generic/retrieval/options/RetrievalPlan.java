package com.shk.generic.retrieval.options;

/**
 * A fully resolved, validated request: every option set, every limit checked.
 *
 * @param minScore          cosine floor for the semantic leg
 * @param keywordMinScore   floor for the keyword leg, in the keyword engine's scale
 * @param maxLlmCalls       hard cap for this request
 * @param estimatedLlmCalls what the chosen transform and reranker are expected to use (already ≤ max)
 */
public record RetrievalPlan(
        String text,
        RetrievalMode mode,
        QueryTransformType queryTransform,
        RerankerType rerank,
        DiversifierType diversify,
        int topK,
        int candidates,
        double minScore,
        double keywordMinScore,
        double mmrLambda,
        int maxLlmCalls,
        int estimatedLlmCalls,
        boolean explain
) {
}
