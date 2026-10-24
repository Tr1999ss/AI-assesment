package com.shk.generic.retrieval;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param defaultTopK       results when the caller doesn't say
 * @param maxTopK           hard cap on results per query
 * @param defaultMinScore   cosine similarity floor (0..1). For text-embedding-3-small, relevant chunks
 *                          typically score 0.45+ and unrelated ones below 0.25
 * @param maxQueryChars     longer queries are rejected
 * @param maxContextChars   cap on source text sent to the chat model when answering
 * @param answerTemperature sampling temperature for answers; low keeps them close to the sources
 */
@ConfigurationProperties("retrieval")
public record RetrievalProperties(
        @DefaultValue("5") int defaultTopK,
        @DefaultValue("20") int maxTopK,
        @DefaultValue("0.25") double defaultMinScore,
        @DefaultValue("2000") int maxQueryChars,
        @DefaultValue("12000") int maxContextChars,
        @DefaultValue("0.2") double answerTemperature
) {
}
