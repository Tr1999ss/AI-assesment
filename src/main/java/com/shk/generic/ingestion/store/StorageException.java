package com.shk.generic.ingestion.store;

/** Embedding or writing chunks failed. Retryable: nothing was left behind. */
public class StorageException extends RuntimeException {

    public StorageException(String message) {
        super(message);
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
