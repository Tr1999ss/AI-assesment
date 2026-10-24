package com.shk.generic.retrieval;

/** The embedding or chat model (or the vector store) failed. Retryable. */
public class RetrievalUnavailableException extends RuntimeException {

    public RetrievalUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
