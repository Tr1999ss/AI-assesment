package com.shk.generic.retrieval.rerank;

import com.shk.generic.retrieval.options.RerankerType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Every {@link Reranker} bean by type. Adding a reranker = adding a bean and an enum constant. */
@Component
public class RerankerRegistry {

    private final Map<RerankerType, Reranker> byType = new EnumMap<>(RerankerType.class);

    public RerankerRegistry(List<Reranker> rerankers) {
        rerankers.forEach(r -> byType.put(r.type(), r));
        for (RerankerType type : RerankerType.values()) {
            if (!byType.containsKey(type)) {
                throw new IllegalStateException("No Reranker bean for " + type);
            }
        }
    }

    public Reranker get(RerankerType type) {
        return byType.get(type);
    }
}
