package com.shk.generic.ingestion.extract;

/** Content was accepted by intake but could not be turned into text. */
public class ExtractionException extends RuntimeException {

    public ExtractionException(String message) {
        super(message);
    }

    public ExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}
