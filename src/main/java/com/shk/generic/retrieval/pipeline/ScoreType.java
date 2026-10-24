package com.shk.generic.retrieval.pipeline;

/**
 * What a hit's {@code score} means. Scores of different types are not comparable:
 * never apply a threshold meant for one type to another.
 */
public enum ScoreType {
    /** Cosine similarity, 0..1. */
    COSINE,
    /** Postgres ts_rank_cd normalized to 0..1 (rank / (rank + 1)). */
    FTS_RANK,
    /** ParadeDB BM25 score, unbounded, higher is better. */
    BM25,
    /** Reciprocal Rank Fusion: sum of 1 / (k + rank) across lists. Small numbers (≈ 0.01-0.05). */
    RRF,
    /** Reranker relevance, normalized to 0..1. */
    RERANK
}
