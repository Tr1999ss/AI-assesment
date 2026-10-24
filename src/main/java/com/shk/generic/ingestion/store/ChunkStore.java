package com.shk.generic.ingestion.store;

import org.springframework.ai.document.Document;

import java.util.List;
import java.util.UUID;

/** Where chunk vectors live. Writing embeds the chunks. */
public interface ChunkStore {

    /** Embeds and upserts; chunks with an existing id are overwritten, so retries are safe. */
    void write(List<Document> chunks);

    /** Removes every chunk of a document. No-op if there are none. */
    void deleteByDocumentId(UUID documentId);
}
