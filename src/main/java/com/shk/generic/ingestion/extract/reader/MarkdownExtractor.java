package com.shk.generic.ingestion.extract.reader;

import com.shk.generic.ingestion.extract.DocumentExtractor;
import com.shk.generic.ingestion.intake.SourceFormat;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One Document per heading section, code block and blockquote.
 * The reader stores the element type under {@code category}, which would collide with
 * our own category tags, so it is moved to {@code md_element}.
 */
@Component
public class MarkdownExtractor implements DocumentExtractor {

    static final String READER_CATEGORY_KEY = "category";
    public static final String MD_ELEMENT = "md_element";

    private static final MarkdownDocumentReaderConfig CONFIG = MarkdownDocumentReaderConfig.builder()
            .withHorizontalRuleCreateDocument(true)
            .withIncludeCodeBlock(true)
            .withIncludeBlockquote(true)
            .build();

    @Override
    public boolean supports(SourceFormat format) {
        return format == SourceFormat.MARKDOWN;
    }

    @Override
    public List<Document> extract(Resource content, String filename) {
        return new MarkdownDocumentReader(content, CONFIG).get().stream()
                .map(MarkdownExtractor::renameCategoryKey)
                .toList();
    }

    private static Document renameCategoryKey(Document doc) {
        if (!doc.getMetadata().containsKey(READER_CATEGORY_KEY)) {
            return doc;
        }
        Map<String, Object> metadata = new HashMap<>(doc.getMetadata());
        metadata.put(MD_ELEMENT, metadata.remove(READER_CATEGORY_KEY));
        return doc.mutate().metadata(metadata).build();
    }
}
