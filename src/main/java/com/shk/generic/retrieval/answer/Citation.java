package com.shk.generic.retrieval.answer;

import com.shk.generic.retrieval.SearchHit;
import org.jspecify.annotations.Nullable;

/** A source the answer cites as {@code [number]}. */
public record Citation(
        int number,
        String documentId,
        String chunkId,
        String source,
        @Nullable String title,
        @Nullable Integer page,
        double score
) {
    public static Citation of(int number, SearchHit hit) {
        return new Citation(number, hit.documentId(), hit.chunkId(), hit.source(), hit.title(), hit.page(), hit.score());
    }
}
