package com.shk.generic.ingestion.category.classifier;

import org.jspecify.annotations.Nullable;

import java.util.List;

/** Raw structured output from the model. Untrusted until {@link ClassificationGuard} has checked it. */
public record Classification(
        @Nullable String primary,
        @Nullable Double primaryConfidence,
        @Nullable List<Overlap> overlaps
) {
    public record Overlap(@Nullable String name, @Nullable Double confidence) {
    }
}
