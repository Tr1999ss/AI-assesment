package com.shk.generic.retrieval.pipeline;

import java.util.List;

/** One retriever's output for one query, best first. {@code source} names it in explain output. */
public record RankedList(String source, List<Candidate> candidates) {

    public RankedList {
        candidates = List.copyOf(candidates);
    }
}
