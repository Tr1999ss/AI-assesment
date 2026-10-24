package com.shk.generic.retrieval.rerank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Reranker settings. API keys come from environment variables (see application.properties)
 * and are masked in {@code toString()} so they can't leak through logs.
 *
 * @param timeout timeout for HTTP rerankers (the LLM reranker uses retrieval.llm-timeout)
 */
@ConfigurationProperties("retrieval.rerank")
public record RerankProperties(
        @DefaultValue("10s") Duration timeout,
        @DefaultValue Llm llm,
        @DefaultValue Cohere cohere,
        @DefaultValue CrossEncoder crossEncoder
) {

    /**
     * @param batchSize            candidates scored per LLM call
     * @param maxCharsPerCandidate candidate text is truncated to this before being sent
     */
    public record Llm(@DefaultValue("10") int batchSize, @DefaultValue("600") int maxCharsPerCandidate) {
    }

    public record Cohere(
            @DefaultValue("") String apiKey,
            @DefaultValue("rerank-v3.5") String model,
            @DefaultValue("https://api.cohere.com") String baseUrl
    ) {
        public boolean configured() {
            return apiKey != null && !apiKey.isBlank();
        }

        @Override
        public String toString() {
            return "Cohere[apiKey=" + (configured() ? "***" : "<unset>") + ", model=" + model + ", baseUrl=" + baseUrl + "]";
        }
    }

    /** A Hugging Face Text Embeddings Inference compatible {@code POST {url}/rerank} endpoint. */
    public record CrossEncoder(@DefaultValue("") String url, @DefaultValue("") String apiKey) {

        public boolean configured() {
            return url != null && !url.isBlank();
        }

        @Override
        public String toString() {
            return "CrossEncoder[url=" + url + ", apiKey=" + (apiKey == null || apiKey.isBlank() ? "<unset>" : "***") + "]";
        }
    }
}
