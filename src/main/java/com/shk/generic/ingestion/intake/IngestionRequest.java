package com.shk.generic.ingestion.intake;

import org.jspecify.annotations.Nullable;
import org.springframework.core.io.Resource;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The single normalized request every intake adapter produces.
 *
 * @param requestedCategories in caller order, first is primary; empty means "let the AI classifier decide"
 * @param submittedBy         authenticated user who submitted it; null for internal calls
 */
public record IngestionRequest(
        String requestId,
        IntakeChannel channel,
        String filename,
        SourceFormat format,
        Resource content,
        Set<String> requestedCategories,
        Map<String, Object> attributes,
        @Nullable String submittedBy
) {
    public IngestionRequest {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(filename, "filename");
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(content, "content");
        requestedCategories = normalizeCategories(requestedCategories);
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    public static IngestionRequest of(IntakeChannel channel, String filename, SourceFormat format,
                                      Resource content, Collection<String> categories,
                                      Map<String, Object> attributes) {
        return new IngestionRequest(UUID.randomUUID().toString(), channel, filename, format,
                content, normalizeCategories(categories), attributes, null);
    }

    public IngestionRequest withSubmittedBy(@Nullable String user) {
        return new IngestionRequest(requestId, channel, filename, format, content, requestedCategories, attributes, user);
    }

    public boolean hasRequestedCategories() {
        return !requestedCategories.isEmpty();
    }

    private static Set<String> normalizeCategories(Collection<String> raw) {
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
