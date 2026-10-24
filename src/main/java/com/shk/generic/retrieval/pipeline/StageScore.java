package com.shk.generic.retrieval.pipeline;

/**
 * A chunk's position at one pipeline stage, for explain output.
 *
 * @param stage e.g. {@code semantic[0]}, {@code keyword[0]}, {@code rrf}, {@code rerank:LLM}, {@code mmr}
 * @param rank  1-based rank within that stage's output
 */
public record StageScore(String stage, int rank, double score, ScoreType scoreType) {
}
