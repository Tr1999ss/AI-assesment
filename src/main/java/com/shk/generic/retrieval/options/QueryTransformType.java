package com.shk.generic.retrieval.options;

/** How the query is rewritten before retrieval. */
public enum QueryTransformType implements RetrievalOption {

    NONE("Search with the query as written.", "free"),
    REWRITE("LLM rewrites a conversational or vague question into a focused search query.",
            "1 LLM call; +0.5-2 s"),
    MULTI_QUERY("LLM writes several alternative phrasings; each is searched and the results fused with RRF. Improves recall for ambiguous questions.",
            "1 LLM call + N extra searches; +1-3 s"),
    HYDE("LLM writes a hypothetical answer passage and the semantic leg searches with it (the keyword leg keeps the original query). Helps short or abstract questions.",
            "1 LLM call; +1-3 s");

    private final String description;
    private final String costNote;

    QueryTransformType(String description, String costNote) {
        this.description = description;
        this.costNote = costNote;
    }

    public int llmCalls() {
        return this == NONE ? 0 : 1;
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
