package com.shk.generic.ingestion.dedup;

import com.shk.generic.ingestion.extract.ExtractedDocument;

public interface ContentHasher {

    /** @return lowercase hex digest identifying the document's content */
    String hash(ExtractedDocument document);
}
