package com.shk.generic.retrieval.answer;

import com.shk.generic.retrieval.SearchHit;

import java.util.List;

/** Builds the chat prompt that answers a question from retrieved sources. */
public interface AnswerPromptBuilder {

    /** @param hits best first; implementations may drop trailing hits to respect a size budget */
    AnswerPrompt build(String question, List<SearchHit> hits);
}
