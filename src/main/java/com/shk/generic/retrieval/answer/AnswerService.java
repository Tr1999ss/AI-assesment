package com.shk.generic.retrieval.answer;

/** Answers questions from stored documents, with citations. */
public interface AnswerService {

    Answer ask(Question question);
}
