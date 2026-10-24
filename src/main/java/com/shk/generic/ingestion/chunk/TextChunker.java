package com.shk.generic.ingestion.chunk;

import org.springframework.ai.document.Document;

import java.util.List;

/** Splits one extracted section into chunks. Section metadata must be carried onto every chunk. */
public interface TextChunker {

    List<Document> split(Document section);
}
