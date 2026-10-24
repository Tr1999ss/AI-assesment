package com.shk.generic.ingestion.registry;

public enum RegistrationStatus {
    /** Hash claimed; downstream stages (classify, embed, store) still in progress. */
    PENDING,
    /** Chunks stored in the vector store. */
    INGESTED
}
