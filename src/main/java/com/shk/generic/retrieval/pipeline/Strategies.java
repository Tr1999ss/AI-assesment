package com.shk.generic.retrieval.pipeline;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Strategy beans indexed by their option enum; fails at startup if an option has no implementation. */
public final class Strategies<E extends Enum<E>, S> {

    private final Map<E, S> byType;

    public Strategies(Class<E> type, List<S> strategies, Function<S, E> typeOf) {
        this.byType = new EnumMap<>(type);
        strategies.forEach(s -> byType.put(typeOf.apply(s), s));
        for (E e : type.getEnumConstants()) {
            if (!byType.containsKey(e)) {
                throw new IllegalStateException("No " + type.getSimpleName() + " strategy bean for " + e);
            }
        }
    }

    public S get(E type) {
        return byType.get(type);
    }
}
