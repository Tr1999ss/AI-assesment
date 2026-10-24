package com.shk.generic.retrieval.keyword;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

import java.util.List;

/** Selects the configured {@link KeywordEngine} and verifies it at startup, so misconfiguration fails fast. */
@Component
public class ActiveKeywordEngine implements SmartInitializingSingleton {

    private final KeywordEngine engine;

    public ActiveKeywordEngine(List<KeywordEngine> engines, KeywordProperties properties) {
        this.engine = engines.stream()
                .filter(e -> e.type() == properties.engine())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No KeywordEngine for " + properties.engine()));
    }

    public KeywordEngine get() {
        return engine;
    }

    @Override
    public void afterSingletonsInstantiated() {
        engine.verify();
    }
}
