package com.shk.generic.ingestion.intake;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/** What a document is. Drives which extractor reads it. */
public enum SourceFormat {

    PDF(List.of("application/pdf"), List.of("pdf")),
    MARKDOWN(List.of("text/markdown", "text/x-markdown"), List.of("md", "markdown")),
    JSON(List.of("application/json"), List.of("json")),
    TEXT(List.of("text/plain"), List.of("txt", "text")),
    UNKNOWN(List.of(), List.of());

    private final List<String> mimeTypes;
    private final List<String> extensions;

    SourceFormat(List<String> mimeTypes, List<String> extensions) {
        this.mimeTypes = mimeTypes;
        this.extensions = extensions;
    }

    public String defaultExtension() {
        return extensions.isEmpty() ? "bin" : extensions.get(0);
    }

    public static Optional<SourceFormat> fromMimeType(@Nullable String mimeType) {
        if (mimeType == null) {
            return Optional.empty();
        }
        String base = mimeType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        return known().filter(f -> f.mimeTypes.contains(base)).findFirst();
    }

    public static Optional<SourceFormat> fromFilename(@Nullable String filename) {
        if (filename == null || filename.lastIndexOf('.') < 0) {
            return Optional.empty();
        }
        String ext = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        return known().filter(f -> f.extensions.contains(ext)).findFirst();
    }

    private static Stream<SourceFormat> known() {
        return Stream.of(values()).filter(f -> f != UNKNOWN);
    }
}
