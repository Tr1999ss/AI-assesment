package com.shk.generic.retrieval.rerank;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.shk.generic.retrieval.llm.LlmCallBudget;
import com.shk.generic.retrieval.options.RerankerType;
import com.shk.generic.retrieval.pipeline.Candidate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Self-hosted cross-encoder behind a Hugging Face Text Embeddings Inference compatible endpoint:
 * {@code POST {url}/rerank {"query", "texts", "raw_scores": false, "truncate": true}} →
 * {@code [{"index", "score"}]} with sigmoid scores in 0..1 (e.g. BAAI/bge-reranker-v2-m3).
 * Enabled only when {@code CROSS_ENCODER_URL} is set; {@code CROSS_ENCODER_API_KEY} is optional.
 */
@Component
public class CrossEncoderReranker implements Reranker {

    private final RerankProperties.CrossEncoder config;
    private final HttpRerankClient client;

    public CrossEncoderReranker(RerankProperties properties) {
        this.config = properties.crossEncoder();
        this.client = config.configured()
                ? new HttpRerankClient("cross-encoder", config.url(), properties.timeout())
                : null;
    }

    @Override
    public RerankerType type() {
        return RerankerType.CROSS_ENCODER;
    }

    @Override
    public Optional<String> unavailableReason() {
        return config.configured() ? Optional.empty() : Optional.of("CROSS_ENCODER_URL is not set");
    }

    @Override
    public List<Candidate> rerank(String query, List<Candidate> candidates, LlmCallBudget budget) {
        if (client == null) {
            throw new RerankException("cross-encoder: not configured");
        }
        if (candidates.isEmpty()) {
            return candidates;
        }
        Request body = new Request(query, candidates.stream().map(c -> c.hit().text()).toList(), false, true);
        Result[] results = client.post("/rerank", config.apiKey(), body, Result[].class);
        double[] scores = new double[candidates.size()];
        for (Result r : results) {
            if (r.index() >= 0 && r.index() < scores.length) {
                scores[r.index()] = r.score();
            }
        }
        return RerankSupport.apply(stage(), candidates, scores);
    }

    record Request(String query, List<String> texts, @JsonProperty("raw_scores") boolean rawScores, boolean truncate) {
    }

    record Result(int index, double score) {
    }
}
