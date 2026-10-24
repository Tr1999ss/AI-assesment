package com.shk.generic.ingestion;

public enum IngestionStatus {
    /** Chunks embedded and stored; the document is searchable. */
    INGESTED,
    /** Same content already registered; the existing document is reported and left unchanged. */
    DUPLICATE
}
