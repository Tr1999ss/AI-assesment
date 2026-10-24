package com.shk.generic.ingestion.dedup;

import com.shk.generic.ingestion.registry.RegisteredDocument;

/** Result of the duplicate check. Sealed so the pipeline must handle both cases. */
public sealed interface DedupOutcome {

    RegisteredDocument document();

    /** New content; this request owns the registry entry and continues down the pipeline. */
    record Fresh(RegisteredDocument document) implements DedupOutcome {
    }

    /** Content already registered; {@code document} is the existing entry, left untouched. */
    record Duplicate(RegisteredDocument document) implements DedupOutcome {
    }
}
