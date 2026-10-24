package com.shk.generic.ingestion.registry;

import com.shk.generic.ingestion.intake.IntakeChannel;
import com.shk.generic.ingestion.intake.SourceFormat;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * @param categories      while PENDING: what the caller requested; once INGESTED: final set, primary first
 * @param primaryCategory set once INGESTED
 * @param chunkCount      set once INGESTED
 */
public record RegisteredDocument(
        UUID id,
        String contentHash,
        String filename,
        SourceFormat format,
        IntakeChannel channel,
        List<String> categories,
        @Nullable String primaryCategory,
        @Nullable Integer chunkCount,
        RegistrationStatus status,
        Instant createdAt,
        Instant updatedAt
) {
    public RegisteredDocument {
        categories = List.copyOf(categories);
    }
}
