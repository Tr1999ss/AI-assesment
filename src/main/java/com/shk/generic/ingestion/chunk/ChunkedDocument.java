package com.shk.generic.ingestion.chunk;

import com.shk.generic.ingestion.category.CategorizedDocument;
import org.springframework.ai.document.Document;

import java.util.List;

/**
 * Output of the chunking stage: chunks ready to embed and store.
 * Each chunk has a stable id, category metadata, and a formatter that controls its embedding text.
 */
public record ChunkedDocument(CategorizedDocument categorized, List<Document> chunks) {

    public ChunkedDocument {
        chunks = List.copyOf(chunks);
    }
}
