package com.shk.generic.retrieval.pipeline;

import com.shk.generic.retrieval.SearchHit;

import java.util.ArrayList;
import java.util.List;

/**
 * A chunk moving through the pipeline: its data, its current score, and how it ranked at every stage so far.
 * Immutable; each stage returns new candidates.
 */
public record Candidate(SearchHit hit, double score, ScoreType scoreType, List<StageScore> trace) {

    public Candidate {
        trace = List.copyOf(trace);
    }

    public static Candidate first(SearchHit hit, String stage, int rank, double score, ScoreType type) {
        return new Candidate(hit, score, type, List.of(new StageScore(stage, rank, score, type)));
    }

    public String chunkId() {
        return hit.chunkId();
    }

    /** A new score from a later stage. */
    public Candidate rescored(String stage, int rank, double newScore, ScoreType newType) {
        return new Candidate(hit, newScore, newType, append(new StageScore(stage, rank, newScore, newType)));
    }

    /** Same score, new position (e.g. MMR reorders without rescoring). */
    public Candidate reranked(String stage, int rank) {
        return new Candidate(hit, score, scoreType, append(new StageScore(stage, rank, score, scoreType)));
    }

    /** Merges another list's trace for the same chunk (fusion). */
    public Candidate withTraceOf(Candidate other) {
        List<StageScore> merged = new ArrayList<>(trace);
        other.trace.stream().filter(s -> !merged.contains(s)).forEach(merged::add);
        return new Candidate(hit, score, scoreType, merged);
    }

    public SearchHit toHit(boolean includeTrace) {
        return hit.withScore(score, scoreType, includeTrace ? trace : null);
    }

    private List<StageScore> append(StageScore s) {
        List<StageScore> next = new ArrayList<>(trace);
        next.add(s);
        return next;
    }
}
