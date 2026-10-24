package com.shk.generic.retrieval;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * One matching chunk.
 *
 * @param score cosine similarity, 0..1, higher is better
 * @param title section heading, when the source had one
 * @param page  PDF page, when the source was a PDF
 */
public record SearchHit(
        String chunkId,
        String documentId,
        String source,
        @Nullable String title,
        @Nullable Integer page,
        List<String> categories,
        @Nullable String primaryCategory,
        int chunkIndex,
        double score,
        String text
) {
    public SearchHit {
        categories = List.copyOf(categories);
    }
}
