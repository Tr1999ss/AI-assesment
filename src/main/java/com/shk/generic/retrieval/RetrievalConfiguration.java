package com.shk.generic.retrieval;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration(proxyBeanMethods = false)
public class RetrievalConfiguration {

    /**
     * Runs retrieval legs in parallel and bounds LLM calls with timeouts. Bounded queue: under overload,
     * submissions are rejected (surfaced as 503) instead of queueing without limit.
     */
    @Bean(name = "retrievalExecutor", destroyMethod = "shutdown")
    public AsyncTaskExecutor retrievalExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("retrieval-");
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(32);
        executor.setQueueCapacity(200);
        executor.initialize();
        return executor;
    }
}
