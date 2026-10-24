package com.shk.generic.retrieval.rerank;

import com.shk.generic.retrieval.llm.LlmCallBudget;
import com.shk.generic.retrieval.options.RerankerType;
import com.shk.generic.retrieval.pipeline.Candidate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class NoopReranker implements Reranker {

    @Override
    public RerankerType type() {
        return RerankerType.NONE;
    }

    @Override
    public List<Candidate> rerank(String query, List<Candidate> candidates, LlmCallBudget budget) {
        return candidates;
    }
}
