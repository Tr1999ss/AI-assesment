package com.shk.generic.ingestion.chunk;

import java.util.UUID;

/** Chunk ids must be stable for a given document and position, so a retried store overwrites instead of duplicating. */
public interface ChunkIdGenerator {

    String chunkId(UUID documentId, int chunkIndex);
}
