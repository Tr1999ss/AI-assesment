package com.shk.generic.retrieval.rerank;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.shk.generic.retrieval.llm.LlmCallBudget;
import com.shk.generic.retrieval.options.RerankerType;
import com.shk.generic.retrieval.pipeline.Candidate;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Cohere Rerank v2 ({@code POST /v2/rerank}). Enabled only when {@code COHERE_API_KEY} is set.
 * relevance_score is already 0..1.
 */
@Component
public class CohereReranker implements Reranker {

    private final RerankProperties.Cohere config;
    private final HttpRerankClient client;

    public CohereReranker(RerankProperties properties) {
        this.config = properties.cohere();
        this.client = new HttpRerankClient("cohere", config.baseUrl(), properties.timeout());
    }

    @Override
    public RerankerType type() {
        return RerankerType.COHERE;
    }

    @Override
    public Optional<String> unavailableReason() {
        return config.configured() ? Optional.empty() : Optional.of("COHERE_API_KEY is not set");
    }

    @Override
    public List<Candidate> rerank(String query, List<Candidate> candidates, LlmCallBudget budget) {
        if (candidates.isEmpty()) {
            return candidates;
        }
        Request body = new Request(config.model(), query,
                candidates.stream().map(c -> c.hit().text()).toList(), candidates.size());
        Response response = client.post("/v2/rerank", config.apiKey(), body, Response.class);
        double[] scores = new double[candidates.size()];
        if (response.results() != null) {
            for (Response.Result r : response.results()) {
                if (r.index() >= 0 && r.index() < scores.length) {
                    scores[r.index()] = r.relevanceScore();
                }
            }
        }
        return RerankSupport.apply(stage(), candidates, scores);
    }

    record Request(String model, String query, List<String> documents, @JsonProperty("top_n") int topN) {
    }

    record Response(@Nullable List<Result> results) {
        record Result(int index, @JsonProperty("relevance_score") double relevanceScore) {
        }
    }
}
