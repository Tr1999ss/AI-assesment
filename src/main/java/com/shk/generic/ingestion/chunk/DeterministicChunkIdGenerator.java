package com.shk.generic.ingestion.chunk;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Name-based UUID of {@code documentId:chunkIndex}; the vector store's id column is a UUID. */
@Component
public class DeterministicChunkIdGenerator implements ChunkIdGenerator {

    @Override
    public String chunkId(UUID documentId, int chunkIndex) {
        return UUID.nameUUIDFromBytes((documentId + ":" + chunkIndex).getBytes(StandardCharsets.UTF_8)).toString();
    }
}
