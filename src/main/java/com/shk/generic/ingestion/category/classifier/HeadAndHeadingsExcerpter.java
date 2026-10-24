package com.shk.generic.ingestion.category.classifier;

import com.shk.generic.ingestion.category.CategoryProperties;
import com.shk.generic.ingestion.extract.ExtractedDocument;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * The first {@code excerptChars} of text plus every section heading, so the model sees both
 * how the document opens and an outline of everything after the cut-off.
 */
@Component
public class HeadAndHeadingsExcerpter implements DocumentExcerpter {

    /** Heading metadata key written by Spring AI's MarkdownDocumentReader. */
    static final String TITLE_KEY = "title";
    private static final int MAX_HEADINGS = 50;

    private final CategoryProperties properties;

    public HeadAndHeadingsExcerpter(CategoryProperties properties) {
        this.properties = properties;
    }

    @Override
    public String excerpt(ExtractedDocument document) {
        String text = document.normalizedText();
        int limit = properties.excerptChars();
        String head = text.length() <= limit ? text : text.substring(0, limit) + " […]";

        List<String> headings = document.sections().stream()
                .map(Document::getMetadata)
                .map(m -> m.get(TITLE_KEY))
                .filter(Objects::nonNull)
                .map(Object::toString)
                .distinct()
                .limit(MAX_HEADINGS)
                .toList();
        if (headings.isEmpty()) {
            return head;
        }
        return head + "\n\nSection headings:\n- " + String.join("\n- ", headings);
    }
}
