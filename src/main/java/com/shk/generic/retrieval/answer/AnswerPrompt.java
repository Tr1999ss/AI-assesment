package com.shk.generic.retrieval.answer;

import com.shk.generic.retrieval.SearchHit;

import java.util.List;

/** @param sources the hits included in the prompt; source {@code [n]} is {@code sources.get(n - 1)} */
public record AnswerPrompt(String system, String user, List<SearchHit> sources) {

    public AnswerPrompt {
        sources = List.copyOf(sources);
    }
}
