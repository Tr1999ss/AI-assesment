package com.shk.generic.ingestion.intake.text;

import com.shk.generic.ingestion.intake.SourceFormat;
import jakarta.validation.constraints.NotBlank;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Inline text body. {@code text} may itself be JSON, Markdown or plain text.
 *
 * @param categories ordered (a JSON array would lose order as a Set); first is primary
 * @param format optional; detected from the content when omitted
 */
public record TextIngestPayload(
        @NotBlank String title,
        @NotBlank String text,
        @Nullable SourceFormat format,
        @Nullable List<String> categories,
        @Nullable Map<String, Object> attributes
) {
}
