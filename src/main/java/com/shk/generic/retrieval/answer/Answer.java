package com.shk.generic.retrieval.answer;

import java.util.List;

/**
 * @param grounded  true when the answer cites at least one source
 * @param citations sources the answer actually cites, in citation-number order
 */
public record Answer(String question, String answer, boolean grounded, List<Citation> citations) {

    public Answer {
        citations = List.copyOf(citations);
    }
}
