package com.shk.generic.ingestion.intake;

/** Transport-independent sanity checks. Throws {@link IntakeRejectedException}. */
public interface IntakeValidator {

    void validate(IngestionRequest request);
}
