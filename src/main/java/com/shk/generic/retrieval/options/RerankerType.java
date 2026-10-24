package com.shk.generic.retrieval.options;

/** Second-pass scoring of the candidate pool against the query. */
public enum RerankerType implements RetrievalOption {

    NONE("Keep the retrieval order.", "free"),
    LLM("The configured chat model scores each candidate's relevance (0-10). Good precision, no extra vendor.",
            "1 LLM call per batch of candidates; +1-5 s"),
    COHERE("Cohere Rerank cross-encoder API. Strong precision at low latency. Requires COHERE_API_KEY.",
            "1 API call (paid per search); +100-400 ms"),
    CROSS_ENCODER("Self-hosted cross-encoder (e.g. bge-reranker via Hugging Face TEI /rerank). Requires CROSS_ENCODER_URL.",
            "1 HTTP call to your server; latency depends on hardware");

    private final String description;
    private final String costNote;

    RerankerType(String description, String costNote) {
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
