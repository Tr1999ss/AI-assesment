package com.shk.generic.ingestion.chunk;

import com.shk.generic.ingestion.category.CategorizedDocument;

/** Pipeline stage: {@link CategorizedDocument} → {@link ChunkedDocument}. */
public interface ChunkingStage {

    ChunkedDocument chunk(CategorizedDocument document);
}
