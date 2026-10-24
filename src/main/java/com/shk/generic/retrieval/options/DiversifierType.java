package com.shk.generic.retrieval.options;

/** Final selection from the candidate pool. */
public enum DiversifierType implements RetrievalOption {

    NONE("Take the top K as ranked.", "free"),
    MMR("Maximal Marginal Relevance: trades relevance against similarity to already-picked chunks, so near-duplicates don't crowd the top K.",
            "1 SQL query over candidate embeddings; ~5-20 ms");

    private final String description;
    private final String costNote;

    DiversifierType(String description, String costNote) {
        this.description = description;
        this.costNote = costNote;
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
