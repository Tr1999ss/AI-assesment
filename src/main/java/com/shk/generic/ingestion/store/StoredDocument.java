package com.shk.generic.ingestion.store;

import com.shk.generic.ingestion.chunk.ChunkedDocument;
import com.shk.generic.ingestion.registry.RegisteredDocument;

/** Output of the storage stage: chunks are searchable and the registry entry is INGESTED. */
public record StoredDocument(ChunkedDocument chunked, RegisteredDocument registration) {
}
