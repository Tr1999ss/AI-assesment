package com.shk.generic.retrieval.rerank;

/** A reranker failed. The message is safe to return to callers: status codes only, never bodies or keys. */
public class RerankException extends RuntimeException {

    public RerankException(String message) {
        super(message);
    }

    public RerankException(String message, Throwable cause) {
        super(message, cause);
    }
}
