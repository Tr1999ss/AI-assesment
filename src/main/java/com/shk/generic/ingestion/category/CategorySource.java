package com.shk.generic.ingestion.category;

public enum CategorySource {
    /** Supplied by the caller and validated against the registry. */
    CALLER,
    /** Chosen by the AI classifier. */
    AI,
    /** Classifier was not confident; the configured fallback category was used. */
    FALLBACK
}
