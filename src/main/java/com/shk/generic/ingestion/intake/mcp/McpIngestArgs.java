package com.shk.generic.ingestion.intake.mcp;

import org.jspecify.annotations.Nullable;

import java.util.List;

/** Exactly one of {@code text} or {@code base64} must be set. */
public record McpIngestArgs(
        String name,
        @Nullable String text,
        @Nullable String base64,
        @Nullable String mimeType,
        @Nullable List<String> categories
) {
}
