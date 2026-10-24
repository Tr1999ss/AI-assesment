package com.shk.generic.retrieval.llm;

/**
 * An optional LLM-backed stage (query transform, LLM rerank) failed, timed out or ran out of budget.
 * The message is safe to show to callers: it never contains provider responses or credentials.
 */
public class LlmStageException extends RuntimeException {

    public LlmStageException(String message) {
        super(message);
    }

    public LlmStageException(String message, Throwable cause) {
        super(message, cause);
    }
}
