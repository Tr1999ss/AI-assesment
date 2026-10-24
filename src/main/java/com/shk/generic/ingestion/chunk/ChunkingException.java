package com.shk.generic.ingestion.chunk;

/** The document could not be chunked within limits. Not retryable as-is. */
public class ChunkingException extends RuntimeException {

    public ChunkingException(String message) {
        super(message);
    }
}
