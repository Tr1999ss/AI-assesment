package com.shk.generic.ingestion.store;

import com.shk.generic.ingestion.chunk.ChunkedDocument;

/** Pipeline stage: {@link ChunkedDocument} → {@link StoredDocument}. */
public interface StorageStage {

    StoredDocument store(ChunkedDocument document);
}
