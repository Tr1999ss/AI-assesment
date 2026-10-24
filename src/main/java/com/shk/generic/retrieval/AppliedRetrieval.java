package com.shk.generic.retrieval;

import com.shk.generic.retrieval.options.DiversifierType;
import com.shk.generic.retrieval.options.QueryTransformType;
import com.shk.generic.retrieval.options.RerankerType;
import com.shk.generic.retrieval.options.RetrievalMode;
import com.shk.generic.retrieval.pipeline.ScoreType;
import org.jspecify.annotations.Nullable;

/**
 * What actually ran for this request: defaults filled in, and any stage that was requested but skipped
 * (e.g. a reranker timeout) reported as NONE, with the reason in the response's {@code warnings}.
 *
 * @param keywordEngine     the full-text engine, when a keyword leg ran
 * @param minScore          cosine floor applied to the semantic leg
 * @param keywordMinScore   floor applied to the keyword leg, in that engine's scale
 * @param rrfK              when results were fused
 * @param mmrLambda         when MMR ran
 * @param llmCalls          LLM calls made by transforms and the LLM reranker
 * @param scoreType         meaning of the hits' {@code score}
 */
public record AppliedRetrieval(
        RetrievalMode mode,
        QueryTransformType queryTransform,
        RerankerType rerank,
        DiversifierType diversify,
        int topK,
        int candidates,
        @Nullable String keywordEngine,
        @Nullable Double minScore,
        @Nullable Double keywordMinScore,
        @Nullable Integer rrfK,
        @Nullable Double mmrLambda,
        int llmCalls,
        ScoreType scoreType
) {
}
