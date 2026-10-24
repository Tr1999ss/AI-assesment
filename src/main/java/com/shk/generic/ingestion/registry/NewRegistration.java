package com.shk.generic.ingestion.registry;

import com.shk.generic.ingestion.intake.IntakeChannel;
import com.shk.generic.ingestion.intake.SourceFormat;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * @param categories as requested by the caller, in order
 * @param ingestedBy authenticated submitter, if any
 */
public record NewRegistration(
        String contentHash,
        String filename,
        SourceFormat format,
        IntakeChannel channel,
        List<String> categories,
        @Nullable String ingestedBy
) {
    public NewRegistration {
        Objects.requireNonNull(contentHash, "contentHash");
        Objects.requireNonNull(filename, "filename");
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(channel, "channel");
        categories = List.copyOf(categories);
    }
}
