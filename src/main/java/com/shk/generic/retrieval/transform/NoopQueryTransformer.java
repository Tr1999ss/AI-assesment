package com.shk.generic.retrieval.transform;

import com.shk.generic.retrieval.llm.LlmCallBudget;
import com.shk.generic.retrieval.options.QueryTransformType;
import org.springframework.stereotype.Component;

@Component
public class NoopQueryTransformer implements QueryTransformer {

    @Override
    public QueryTransformType type() {
        return QueryTransformType.NONE;
    }

    @Override
    public TransformedQuery transform(String query, LlmCallBudget budget) {
        return TransformedQuery.unchanged(query);
    }
}
