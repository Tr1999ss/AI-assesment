package com.shk.generic.ingestion.intake;

/**
 * Normalizes one transport's raw input into an {@link IngestionRequest}.
 * One implementation per transport, not per file format.
 */
public interface IntakeAdapter<S> {

    IngestionRequest adapt(S source);
}
