package com.shk.generic.ingestion.extract;

import com.shk.generic.ingestion.intake.IngestionRequest;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Objects;

/**
 * Output of the extraction stage.
 *
 * @param sections       reader output (pages, Markdown sections, JSON entries), text as-is for embedding
 * @param normalizedText canonical form of the whole document; what the duplicate check hashes
 */
public record ExtractedDocument(
        IngestionRequest request,
        List<Document> sections,
        String normalizedText
) {
    public ExtractedDocument {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(normalizedText, "normalizedText");
        sections = List.copyOf(sections);
    }
}
