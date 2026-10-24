package com.shk.generic.retrieval.transform;

import org.jspecify.annotations.Nullable;

/** Cleans model output used as search text. */
final class LlmText {

    private LlmText() {
    }

    /** Strips whitespace and wrapping quotes, collapses newlines, truncates; null/blank → empty. */
    static String clean(@Nullable String raw, int maxChars) {
        if (raw == null) {
            return "";
        }
        String s = raw.strip().replaceAll("\\s+", " ");
        if (s.length() >= 2 && (s.startsWith("\"") && s.endsWith("\"") || s.startsWith("'") && s.endsWith("'"))) {
            s = s.substring(1, s.length() - 1).strip();
        }
        return s.length() > maxChars ? s.substring(0, maxChars) : s;
    }
}
