package com.shk.generic.ingestion.category;

import com.shk.generic.ingestion.extract.ExtractedDocument;
import com.shk.generic.ingestion.registry.RegisteredDocument;

/** Pipeline stage: {@link ExtractedDocument} → {@link CategorizedDocument}. */
public interface CategorizationStage {

    CategorizedDocument categorize(ExtractedDocument document, RegisteredDocument registration);
}
