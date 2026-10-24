package com.shk.generic.retrieval.options;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Common shape of every selectable retrieval option. Constant names are the canonical values
 * (MCP renders them as JSON-schema enums); REST input is parsed case-insensitively and accepts '-' for '_'.
 */
public interface RetrievalOption {

    String name();

    /** One line: what it is good at. */
    String description();

    /** Rough cost and latency, for callers deciding what to enable. */
    String costNote();

    static <E extends Enum<E> & RetrievalOption> E parse(Class<E> type, String parameter, String raw) {
        String normalized = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return Arrays.stream(type.getEnumConstants())
                .filter(e -> e.name().equals(normalized))
                .findFirst()
                .orElseThrow(() -> new InvalidRetrievalOptionException(parameter, raw, values(type)));
    }

    static <E extends Enum<E>> List<String> values(Class<E> type) {
        return Arrays.stream(type.getEnumConstants()).map(Enum::name).toList();
    }
}
