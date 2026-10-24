package com.shk.generic.retrieval;

import com.shk.generic.retrieval.llm.LlmCallExecutor;
import com.shk.generic.retrieval.options.DiversifierType;
import com.shk.generic.retrieval.options.QueryTransformType;
import com.shk.generic.retrieval.options.RerankerType;
import com.shk.generic.retrieval.options.RetrievalDefaultsProperties;
import com.shk.generic.retrieval.options.RetrievalMode;
import com.shk.generic.retrieval.pipeline.Candidate;
import com.shk.generic.retrieval.pipeline.ScoreType;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

import java.time.Duration;
import java.util.List;

/** Builders shared by retrieval unit tests. */
public final class RetrievalTestSupport {

    private RetrievalTestSupport() {
    }

    public static RetrievalDefaultsProperties defaults(int maxLlmCalls, Duration timeout) {
        return new RetrievalDefaultsProperties(RetrievalMode.SEMANTIC, QueryTransformType.NONE, RerankerType.NONE,
                DiversifierType.NONE, 30, 100, maxLlmCalls, timeout);
    }

    public static LlmCallExecutor llmExecutor(Duration timeout) {
        return new LlmCallExecutor(new SimpleAsyncTaskExecutor("test-llm-"), defaults(6, timeout));
    }

    public static SearchHit hit(String chunkId, String text) {
        return new SearchHit(chunkId, "doc-" + chunkId, chunkId + ".md", null, null, List.of("hr"), "hr", 0, 0.5, text);
    }

    public static Candidate candidate(String chunkId, double score) {
        return Candidate.first(hit(chunkId, "text of " + chunkId), "test", 1, score, ScoreType.COSINE);
    }
}
