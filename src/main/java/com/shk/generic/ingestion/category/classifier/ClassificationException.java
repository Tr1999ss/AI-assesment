package com.shk.generic.ingestion.category.classifier;

/** The classifier model could not be reached or returned unusable output. Retryable. */
public class ClassificationException extends RuntimeException {

    public ClassificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
