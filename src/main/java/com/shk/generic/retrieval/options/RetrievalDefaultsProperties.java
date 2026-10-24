package com.shk.generic.retrieval.options;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Defaults applied when a request doesn't choose, plus hard per-request limits.
 * The default pipeline is plain semantic search; change it only when evaluation numbers justify it.
 *
 * @param defaultCandidates     candidate pool size when fusion, reranking or MMR is active
 * @param maxCandidates         hard cap on the candidate pool
 * @param maxLlmCallsPerRequest hard cap on LLM calls made by query transforms and the LLM reranker
 * @param llmTimeout            per-call timeout for those LLM calls
 */
@ConfigurationProperties("retrieval")
public record RetrievalDefaultsProperties(
        @DefaultValue("semantic") RetrievalMode defaultMode,
        @DefaultValue("none") QueryTransformType defaultQueryTransform,
        @DefaultValue("none") RerankerType defaultRerank,
        @DefaultValue("none") DiversifierType defaultDiversify,
        @DefaultValue("30") int defaultCandidates,
        @DefaultValue("100") int maxCandidates,
        @DefaultValue("6") int maxLlmCallsPerRequest,
        @DefaultValue("15s") Duration llmTimeout
) {
}
