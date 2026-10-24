package com.shk.generic.retrieval.rerank;

import com.shk.generic.retrieval.llm.LlmCallBudget;
import com.shk.generic.retrieval.options.RerankerType;
import com.shk.generic.retrieval.pipeline.Candidate;

import java.util.List;
import java.util.Optional;

/**
 * Re-scores a candidate pool against the query. Output is every input candidate, best first,
 * rescored with stage {@code rerank:<TYPE>} and a 0..1 {@code RERANK} score.
 */
public interface Reranker {

    RerankerType type();

    /** Empty when usable; otherwise why not (e.g. "COHERE_API_KEY is not set"). */
    default Optional<String> unavailableReason() {
        return Optional.empty();
    }

    /** LLM calls this reranker would make for a pool of this size (for the per-request budget). */
    default int llmCalls(int candidates) {
        return 0;
    }

    /** @throws RerankException on failure; the pipeline then keeps the previous order and reports a warning */
    List<Candidate> rerank(String query, List<Candidate> candidates, LlmCallBudget budget);

    default String stage() {
        return "rerank:" + type().name();
    }
}
