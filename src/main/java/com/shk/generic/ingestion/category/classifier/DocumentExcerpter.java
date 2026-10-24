package com.shk.generic.ingestion.category.classifier;

import com.shk.generic.ingestion.extract.ExtractedDocument;

/** Chooses what part of a document the classifier sees, keeping prompt cost flat for large documents. */
public interface DocumentExcerpter {

    String excerpt(ExtractedDocument document);
}
