package com.shk.generic.retrieval;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.shk.generic.retrieval.pipeline.ScoreType;
import com.shk.generic.retrieval.pipeline.StageScore;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * One matching chunk.
 *
 * @param score     the final stage's score; its meaning depends on {@code scoreType}, and scores of different
 *                  types are not comparable
 * @param title     section heading, when the source had one
 * @param page      PDF page, when the source was a PDF
 * @param trace     rank and score at every pipeline stage; only with {@code explain=true}
 */
public record SearchHit(
        String chunkId,
        String documentId,
        String source,
        @Nullable String title,
        @Nullable Integer page,
        List<String> categories,
        @Nullable String primaryCategory,
        int chunkIndex,
        double score,
        String text,
        ScoreType scoreType,
        @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable List<StageScore> trace
) {
    public SearchHit {
        categories = List.copyOf(categories);
        scoreType = scoreType == null ? ScoreType.COSINE : scoreType;
        trace = trace == null ? null : List.copyOf(trace);
    }

    /** A cosine-scored hit without trace (the semantic retriever's raw output). */
    public SearchHit(String chunkId, String documentId, String source, @Nullable String title, @Nullable Integer page,
                     List<String> categories, @Nullable String primaryCategory, int chunkIndex, double score,
                     String text) {
        this(chunkId, documentId, source, title, page, categories, primaryCategory, chunkIndex, score, text,
                ScoreType.COSINE, null);
    }

    public SearchHit withScore(double newScore, ScoreType newType, @Nullable List<StageScore> newTrace) {
        return new SearchHit(chunkId, documentId, source, title, page, categories, primaryCategory, chunkIndex,
                newScore, text, newType, newTrace);
    }
}
