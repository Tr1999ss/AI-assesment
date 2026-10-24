package com.shk.generic.retrieval.eval;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * Metrics per configuration, each compared against the baseline (the first configuration).
 *
 * @param seeded corpus files newly ingested for this run (0 if they were already present)
 */
public record EvaluationReport(
        String goldenSet,
        int questions,
        int k,
        String baseline,
        int seeded,
        List<ConfigResult> results
) {
    public EvaluationReport {
        results = List.copyOf(results);
    }

    /**
     * @param hitRate      mean hit@k
     * @param recall       mean recall@k
     * @param mrr          mean reciprocal rank within k
     * @param errors       questions that failed outright (counted as misses)
     * @param degraded     questions where a requested stage was skipped (see the API's warnings)
     * @param delta        difference to the baseline (this − baseline); null for the baseline itself
     * @param perQuestion  only when details were requested
     */
    public record ConfigResult(
            String configuration,
            double hitRate,
            double recall,
            double mrr,
            double latencyMeanMs,
            long latencyP50Ms,
            long latencyP95Ms,
            double avgLlmCalls,
            int errors,
            int degraded,
            @Nullable Delta delta,
            @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable List<QuestionResult> perQuestion
    ) {
    }

    public record Delta(double hitRate, double recall, double mrr, long latencyP50Ms) {
    }

    /**
     * @param topSources "source#section" of the top hits, for diagnosing misses
     * @param error      failure message, if the search failed
     */
    public record QuestionResult(String id, double hit, double recall, double reciprocalRank, long latencyMs,
                                 List<String> topSources, @Nullable String error) {
    }

    /** Markdown table, for logs and docs. */
    public String toMarkdown() {
        StringBuilder sb = new StringBuilder();
        sb.append("Golden set ").append(goldenSet).append(": ").append(questions).append(" questions, k=").append(k)
                .append(", baseline=").append(baseline).append("\n\n")
                .append("| configuration | hit@k | recall@k | MRR | Δ hit | Δ recall | Δ MRR | p50 ms | p95 ms | LLM calls | errors | degraded |\n")
                .append("|---|---|---|---|---|---|---|---|---|---|---|---|\n");
        for (ConfigResult r : results) {
            sb.append(String.format(Locale.ROOT, "| %s | %.3f | %.3f | %.3f | %s | %s | %s | %d | %d | %.1f | %d | %d |%n",
                    r.configuration(), r.hitRate(), r.recall(), r.mrr(),
                    delta(r.delta() == null ? null : r.delta().hitRate()),
                    delta(r.delta() == null ? null : r.delta().recall()),
                    delta(r.delta() == null ? null : r.delta().mrr()),
                    r.latencyP50Ms(), r.latencyP95Ms(), r.avgLlmCalls(), r.errors(), r.degraded()));
        }
        return sb.toString();
    }

    private static String delta(@Nullable Double d) {
        return d == null ? "baseline" : String.format(Locale.ROOT, "%+.3f", d);
    }
}
