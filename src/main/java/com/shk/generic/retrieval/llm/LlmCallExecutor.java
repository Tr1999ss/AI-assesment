package com.shk.generic.retrieval.llm;

import com.shk.generic.retrieval.options.RetrievalDefaultsProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * Runs one LLM call for a retrieval stage: charges the request's budget, enforces the timeout, and turns
 * any failure into an {@link LlmStageException} with a caller-safe message (provider details are only logged).
 */
@Slf4j
@Component
public class LlmCallExecutor {

    private final AsyncTaskExecutor executor;
    private final Duration timeout;

    public LlmCallExecutor(@Qualifier("retrievalExecutor") AsyncTaskExecutor executor,
                           RetrievalDefaultsProperties properties) {
        this.executor = executor;
        this.timeout = properties.llmTimeout();
    }

    public <T> T call(String purpose, LlmCallBudget budget, Supplier<T> call) {
        return callAll(purpose, budget, List.of(call)).get(0);
    }

    /**
     * Runs several calls concurrently under one shared deadline. Budget is charged for all calls up front,
     * so a request can't start work it isn't allowed to finish.
     */
    public <T> List<T> callAll(String purpose, LlmCallBudget budget, List<Supplier<T>> calls) {
        for (int i = 0; i < calls.size(); i++) {
            budget.acquire(purpose);
        }
        List<CompletableFuture<T>> futures = calls.stream().map(c -> CompletableFuture.supplyAsync(c, executor)).toList();
        long deadline = System.nanoTime() + timeout.toNanos();
        List<T> results = new ArrayList<>(futures.size());
        try {
            for (CompletableFuture<T> f : futures) {
                long remaining = Math.max(0, deadline - System.nanoTime());
                results.add(f.get(remaining, TimeUnit.NANOSECONDS));
            }
            return results;
        } catch (TimeoutException e) {
            throw new LlmStageException(purpose + ": timed out after " + timeout.toSeconds() + "s", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmStageException(purpose + ": interrupted", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            log.warn("LLM call for {} failed", purpose, cause);
            throw new LlmStageException(purpose + ": model call failed (" + cause.getClass().getSimpleName() + ")", cause);
        } finally {
            futures.forEach(f -> f.cancel(true));
        }
    }
}
