package com.shk.generic.ingestion.category;

import com.shk.generic.ingestion.extract.ExtractedDocument;
import com.shk.generic.ingestion.registry.RegisteredDocument;

/** Output of the categorization stage; input to chunk and embed. */
public record CategorizedDocument(
        ExtractedDocument extracted,
        RegisteredDocument registration,
        CategorySet categories,
        CategorySource source
) {
}
