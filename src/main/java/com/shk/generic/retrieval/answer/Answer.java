package com.shk.generic.retrieval.answer;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.shk.generic.retrieval.AppliedRetrieval;
import com.shk.generic.retrieval.RetrievalExplanation;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * @param grounded  true when the answer cites at least one source
 * @param citations sources the answer actually cites, in citation-number order
 * @param retrieval the retrieval settings that actually ran
 * @param warnings  retrieval stages that were requested but degraded or skipped
 * @param explain   per-stage retrieval detail; only with {@code explain=true}
 */
public record Answer(
        String question,
        String answer,
        boolean grounded,
        List<Citation> citations,
        @Nullable AppliedRetrieval retrieval,
        List<String> warnings,
        @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable RetrievalExplanation explain
) {
    public Answer {
        citations = List.copyOf(citations);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public Answer(String question, String answer, boolean grounded, List<Citation> citations) {
        this(question, answer, grounded, citations, null, List.of(), null);
    }
}
