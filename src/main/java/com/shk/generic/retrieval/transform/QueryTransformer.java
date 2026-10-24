package com.shk.generic.retrieval.transform;

import com.shk.generic.retrieval.llm.LlmCallBudget;
import com.shk.generic.retrieval.options.QueryTransformType;

/** Rewrites the query before retrieval. */
public interface QueryTransformer {

    QueryTransformType type();

    /** @throws com.shk.generic.retrieval.llm.LlmStageException on failure; the pipeline then uses the query as written */
    TransformedQuery transform(String query, LlmCallBudget budget);
}
