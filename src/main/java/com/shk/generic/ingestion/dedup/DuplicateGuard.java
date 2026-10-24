package com.shk.generic.ingestion.dedup;

import com.shk.generic.ingestion.extract.ExtractedDocument;

/** Pipeline stage: decides whether extracted content is new or already ingested. */
public interface DuplicateGuard {

    DedupOutcome check(ExtractedDocument document);

    /** Gives up a {@link DedupOutcome.Fresh} claim after a later stage failed, so the content can be retried. */
    void release(DedupOutcome.Fresh claim);
}
