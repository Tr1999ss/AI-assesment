package com.shk.generic.retrieval;

import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A search as the caller asked for it. Defaults and limits are applied by the search service.
 *
 * @param categories empty means all categories
 * @param topK       null means the configured default
 * @param minScore   null means the configured default
 */
public record SearchQuery(
        String text,
        Set<String> categories,
        CategoryScope scope,
        @Nullable Integer topK,
        @Nullable Double minScore
) {
    public SearchQuery {
        text = text == null ? "" : text.strip();
        categories = normalize(categories);
        scope = scope == null ? CategoryScope.ANY : scope;
    }

    public static SearchQuery of(String text, @Nullable Collection<String> categories) {
        return new SearchQuery(text, categories == null ? Set.of() : new LinkedHashSet<>(categories),
                CategoryScope.ANY, null, null);
    }

    private static Set<String> normalize(@Nullable Collection<String> raw) {
        if (raw == null) {
            return Set.of();
        }
        return raw.stream()
                .filter(Objects::nonNull)
                .map(c -> c.trim().toLowerCase(Locale.ROOT))
                .filter(c -> !c.isEmpty())
                .collect(Collectors.collectingAndThen(
                        Collectors.toCollection(LinkedHashSet::new), Collections::unmodifiableSet));
    }
}
