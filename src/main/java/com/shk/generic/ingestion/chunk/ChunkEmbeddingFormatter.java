package com.shk.generic.ingestion.chunk;

import com.shk.generic.ingestion.extract.DefaultExtractionStage;
import org.springframework.ai.document.ContentFormatter;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.MetadataMode;

/**
 * Decides the text that is embedded (and shown to the LLM) for a chunk.
 * <p>
 * Spring AI's default formatter prepends every metadata entry, which would embed ids and hashes
 * into the vector. This one adds only a short context header (source file and section heading)
 * so an isolated chunk like "Twenty days per year." still carries what it is about.
 * Categories are deliberately not embedded: they are filtered on via metadata instead.
 */
public final class ChunkEmbeddingFormatter implements ContentFormatter {

    public static final ChunkEmbeddingFormatter INSTANCE = new ChunkEmbeddingFormatter();

    /** Heading metadata key written by Spring AI's MarkdownDocumentReader. */
    static final String TITLE = "title";

    private ChunkEmbeddingFormatter() {
    }

    @Override
    public String format(Document document, MetadataMode metadataMode) {
        String text = document.getText() == null ? "" : document.getText();
        if (metadataMode == MetadataMode.NONE) {
            return text;
        }
        StringBuilder header = new StringBuilder();
        Object source = document.getMetadata().get(DefaultExtractionStage.SOURCE);
        if (source != null) {
            header.append("Document: ").append(source).append('\n');
        }
        Object title = document.getMetadata().get(TITLE);
        if (title != null && !title.toString().isBlank()) {
            header.append("Section: ").append(title).append('\n');
        }
        return header.isEmpty() ? text : header + "\n" + text;
    }
}
