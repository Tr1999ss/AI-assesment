package com.shk.generic.ingestion.registry;

import com.shk.generic.ingestion.intake.IntakeChannel;
import com.shk.generic.ingestion.intake.SourceFormat;

import java.util.List;
import java.util.Objects;

/** @param categories as requested by the caller, in order */
public record NewRegistration(
        String contentHash,
        String filename,
        SourceFormat format,
        IntakeChannel channel,
        List<String> categories
) {
    public NewRegistration {
        Objects.requireNonNull(contentHash, "contentHash");
        Objects.requireNonNull(filename, "filename");
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(channel, "channel");
        categories = List.copyOf(categories);
    }
}
