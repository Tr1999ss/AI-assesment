package com.shk.generic.ingestion.extract;

import org.springframework.ai.document.Document;

import java.util.List;

/**
 * Produces the canonical text used for duplicate detection.
 * Never fed to the embedder, so it may be lossy.
 */
public interface TextNormalizer {

    String normalize(List<Document> sections);
}
