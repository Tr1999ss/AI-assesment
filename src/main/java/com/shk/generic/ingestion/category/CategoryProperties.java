package com.shk.generic.ingestion.category;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param fallback             category used when the classifier is not confident; must exist in the registry
 * @param primaryMinConfidence below this the classifier's primary pick is not trusted
 * @param overlapMinConfidence below this an overlap is dropped
 * @param maxOverlaps          cap on overlaps per document
 * @param excerptChars         how much document text the classifier sees
 */
@ConfigurationProperties("ingestion.category")
public record CategoryProperties(
        @DefaultValue("general") String fallback,
        @DefaultValue("0.5") double primaryMinConfidence,
        @DefaultValue("0.6") double overlapMinConfidence,
        @DefaultValue("3") int maxOverlaps,
        @DefaultValue("3000") int excerptChars
) {
}
