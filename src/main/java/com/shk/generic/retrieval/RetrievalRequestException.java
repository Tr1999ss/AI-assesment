package com.shk.generic.retrieval;

/** The caller's search or question is invalid (blank, too long, out-of-range parameters). */
public class RetrievalRequestException extends RuntimeException {

    public RetrievalRequestException(String message) {
        super(message);
    }
}
