package com.shk.generic.ingestion.category;

import com.shk.generic.ingestion.extract.ExtractedDocument;

/** One way of deciding a document's categories. One implementation per branch. */
public interface CategoryResolver {

    CategoryResolution resolve(ExtractedDocument document);
}
