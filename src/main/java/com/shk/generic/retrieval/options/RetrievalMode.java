package com.shk.generic.retrieval.options;

/** Which retrievers produce candidates. */
public enum RetrievalMode implements RetrievalOption {

    SEMANTIC("Embedding similarity. Finds paraphrases and synonyms (\"vacation\" ↔ \"annual leave\"); weak on exact codes and rare names.",
            "1 embedding call per query; ~100-300 ms"),
    KEYWORD("Full-text match (Postgres FTS, or BM25 when enabled). Finds exact terms, codes (ERR-4012), names, acronyms; misses paraphrases.",
            "no model calls; ~5-30 ms"),
    HYBRID("Semantic and keyword run in parallel, fused with Reciprocal Rank Fusion. Best general default for mixed queries.",
            "1 embedding call; latency ≈ the slower leg");

    private final String description;
    private final String costNote;

    RetrievalMode(String description, String costNote) {
        this.description = description;
        this.costNote = costNote;
    }

    public boolean usesSemantic() {
        return this != KEYWORD;
    }

    public boolean usesKeyword() {
        return this != SEMANTIC;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public String costNote() {
        return costNote;
    }
}
