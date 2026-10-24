package com.shk.generic.ingestion;

import com.shk.generic.ingestion.intake.SourceFormat;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * @param primaryCategory null until known (e.g. a duplicate of a document still in progress)
 * @param categories      primary first, then overlaps
 */
public record IngestionResult(
        String requestId,
        @Nullable String documentId,
        IngestionStatus status,
        String filename,
        SourceFormat format,
        @Nullable String primaryCategory,
        List<String> categories,
        int chunkCount
) {
    public IngestionResult {
        categories = List.copyOf(categories);
    }
}
