package com.shk.generic.retrieval.eval;

import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Retrieval evaluation against the live knowledge base. kb-admin only (under /api/admin/**): it can ingest
 * the sample corpus and makes many model calls. Synchronous; LLM-heavy configurations take minutes.
 */
@RestController
@RequestMapping("/api/admin/evaluation")
public class EvaluationController {

    private final EvaluationService evaluation;

    public EvaluationController(EvaluationService evaluation) {
        this.evaluation = evaluation;
    }

    @PostMapping
    public EvaluationReport evaluate(@RequestBody(required = false) @Nullable EvaluationRequest request) {
        EvaluationRequest r = request == null ? new EvaluationRequest(null, null, null, null, null) : request;
        GoldenSet set = r.goldenSet() != null ? r.goldenSet() : evaluation.loadDefault();
        int seeded = Boolean.TRUE.equals(r.seedCorpus()) ? evaluation.seedCorpus(set) : 0;
        List<EvalConfig> configs = r.configurations() == null || r.configurations().isEmpty()
                ? evaluation.defaultConfigurations()
                : r.configurations().stream().map(EvalConfig::parse).toList();
        int k = r.k() == null ? 5 : Math.max(1, Math.min(20, r.k()));
        return evaluation.evaluate(set, configs, k, Boolean.TRUE.equals(r.details()), seeded);
    }

    /**
     * @param seedCorpus     ingest eval/corpus first (idempotent: duplicates are skipped)
     * @param k              cutoff for hit@k / recall@k / MRR (default 5, max 20)
     * @param configurations specs like "semantic", "hybrid+rerank:llm+mmr"; the first is the baseline.
     *                       Default: every technique alone plus key combinations, baseline = semantic
     * @param details        include per-question results
     * @param goldenSet      inline golden set; default eval/golden-set.json
     */
    public record EvaluationRequest(@Nullable Boolean seedCorpus, @Nullable Integer k,
                                    @Nullable List<String> configurations, @Nullable Boolean details,
                                    @Nullable GoldenSet goldenSet) {
    }
}
