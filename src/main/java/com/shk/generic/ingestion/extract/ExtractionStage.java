package com.shk.generic.ingestion.extract;

import com.shk.generic.ingestion.intake.IngestionRequest;

/** Pipeline stage: {@link IngestionRequest} → {@link ExtractedDocument}. */
public interface ExtractionStage {

    ExtractedDocument extract(IngestionRequest request);
}
