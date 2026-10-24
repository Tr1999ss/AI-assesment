package com.shk.generic.retrieval.llm;

import java.util.concurrent.atomic.AtomicInteger;

/** Per-request cap on LLM calls made by retrieval stages. Thread-safe. */
public final class LlmCallBudget {

    private final int max;
    private final AtomicInteger used = new AtomicInteger();

    public LlmCallBudget(int max) {
        this.max = max;
    }

    /** @throws LlmStageException if the cap is reached */
    public void acquire(String purpose) {
        if (used.incrementAndGet() > max) {
            used.decrementAndGet();
            throw new LlmStageException(purpose + ": LLM call limit of " + max + " per request reached");
        }
    }

    public int used() {
        return used.get();
    }

    public int max() {
        return max;
    }
}
