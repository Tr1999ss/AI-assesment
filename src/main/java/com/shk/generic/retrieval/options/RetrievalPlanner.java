package com.shk.generic.retrieval.options;

import com.shk.generic.retrieval.RetrievalProperties;
import com.shk.generic.retrieval.SearchQuery;
import com.shk.generic.retrieval.diversify.MmrProperties;
import com.shk.generic.retrieval.keyword.KeywordProperties;
import com.shk.generic.retrieval.rerank.Reranker;
import com.shk.generic.retrieval.rerank.RerankerRegistry;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Turns what the caller asked for into a {@link RetrievalPlan}: fills defaults from configuration and rejects,
 * before any work starts, anything out of range, unavailable, or over the per-request LLM budget.
 * Every rejection is an {@link InvalidRetrievalOptionException} (HTTP 400 / MCP tool error) naming the valid choices.
 */
@Component
public class RetrievalPlanner {

    private final RetrievalProperties basics;
    private final RetrievalDefaultsProperties defaults;
    private final KeywordProperties keyword;
    private final MmrProperties mmr;
    private final RerankerRegistry rerankers;

    public RetrievalPlanner(RetrievalProperties basics, RetrievalDefaultsProperties defaults, KeywordProperties keyword,
                            MmrProperties mmr, RerankerRegistry rerankers) {
        this.basics = basics;
        this.defaults = defaults;
        this.keyword = keyword;
        this.mmr = mmr;
        this.rerankers = rerankers;
    }

    public RetrievalPlan plan(SearchQuery query) {
        RetrievalOptions o = query.options();
        RetrievalMode mode = o.mode() != null ? o.mode() : defaults.defaultMode();
        QueryTransformType transform = o.queryTransform() != null ? o.queryTransform() : defaults.defaultQueryTransform();
        RerankerType rerank = o.rerank() != null ? o.rerank() : defaults.defaultRerank();
        DiversifierType diversify = o.diversify() != null ? o.diversify() : defaults.defaultDiversify();

        int topK = topK(query);
        double minScore = minScore(query);
        Reranker reranker = available(rerank);
        int candidates = candidates(o, topK, mode, transform, rerank, diversify);
        double lambda = lambda(o);

        int llmCalls = transform.llmCalls() + reranker.llmCalls(candidates);
        if (llmCalls > defaults.maxLlmCallsPerRequest()) {
            throw new InvalidRetrievalOptionException("llmCalls", "queryTransform=" + transform + " with rerank="
                    + rerank + " over " + candidates + " candidates needs " + llmCalls + " LLM calls; the limit is "
                    + defaults.maxLlmCallsPerRequest() + " per request. Lower 'candidates' or drop one of them.");
        }
        return new RetrievalPlan(query.text(), mode, transform, rerank, diversify, topK, candidates, minScore,
                keyword.minScore(), lambda, defaults.maxLlmCallsPerRequest(), llmCalls, o.explain());
    }

    /** Whether this rerank option can be used right now, and if not, why. */
    public Optional<String> unavailableReason(RerankerType type) {
        return rerankers.get(type).unavailableReason();
    }

    private Reranker available(RerankerType type) {
        Reranker reranker = rerankers.get(type);
        reranker.unavailableReason().ifPresent(reason -> {
            List<String> usable = Arrays.stream(RerankerType.values())
                    .filter(t -> rerankers.get(t).unavailableReason().isEmpty())
                    .map(Enum::name).toList();
            throw new InvalidRetrievalOptionException("rerank", "rerank=" + type + " is not available (" + reason
                    + "); available rerankers: " + usable);
        });
        return reranker;
    }

    private int topK(SearchQuery query) {
        if (query.topK() == null) {
            return basics.defaultTopK();
        }
        if (query.topK() < 1 || query.topK() > basics.maxTopK()) {
            throw new InvalidRetrievalOptionException("topK", "topK must be between 1 and " + basics.maxTopK());
        }
        return query.topK();
    }

    private double minScore(SearchQuery query) {
        if (query.minScore() == null) {
            return basics.defaultMinScore();
        }
        if (query.minScore() < 0.0 || query.minScore() > 1.0) {
            throw new InvalidRetrievalOptionException("minScore", "minScore must be between 0 and 1");
        }
        return query.minScore();
    }

    /**
     * A pool larger than topK only helps when something reorders or merges candidates; otherwise
     * retrieving topK keeps plain semantic search exactly as before.
     */
    private int candidates(RetrievalOptions o, int topK, RetrievalMode mode, QueryTransformType transform,
                           RerankerType rerank, DiversifierType diversify) {
        int max = defaults.maxCandidates();
        if (o.candidates() != null) {
            if (o.candidates() < topK || o.candidates() > max) {
                throw new InvalidRetrievalOptionException("candidates",
                        "candidates must be between topK (" + topK + ") and " + max);
            }
            return o.candidates();
        }
        boolean needsPool = mode == RetrievalMode.HYBRID || transform == QueryTransformType.MULTI_QUERY
                || rerank != RerankerType.NONE || diversify != DiversifierType.NONE;
        return needsPool ? Math.min(max, Math.max(topK, defaults.defaultCandidates())) : topK;
    }

    private double lambda(RetrievalOptions o) {
        if (o.mmrLambda() == null) {
            return mmr.lambda();
        }
        if (o.mmrLambda() < 0.0 || o.mmrLambda() > 1.0) {
            throw new InvalidRetrievalOptionException("mmrLambda", "mmrLambda must be between 0 and 1");
        }
        return o.mmrLambda();
    }
}
