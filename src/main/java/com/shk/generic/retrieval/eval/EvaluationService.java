package com.shk.generic.retrieval.eval;

import com.shk.generic.ingestion.IngestionResult;
import com.shk.generic.ingestion.IngestionService;
import com.shk.generic.ingestion.IngestionStatus;
import com.shk.generic.ingestion.intake.IngestionRequest;
import com.shk.generic.ingestion.intake.IntakeChannel;
import com.shk.generic.ingestion.intake.NamedByteArrayResource;
import com.shk.generic.ingestion.intake.SourceFormat;
import com.shk.generic.retrieval.CategoryScope;
import com.shk.generic.retrieval.SearchHit;
import com.shk.generic.retrieval.SearchQuery;
import com.shk.generic.retrieval.SearchResult;
import com.shk.generic.retrieval.options.RerankerType;
import com.shk.generic.retrieval.options.RetrievalOptions;
import com.shk.generic.retrieval.options.RetrievalPlanner;
import com.shk.generic.retrieval.search.SearchService;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Runs a golden set through several retrieval configurations via the real {@link SearchService}
 * (same validation, filters and limits as the API) and reports hit@k, recall@k, MRR and latency,
 * each compared against the first configuration (the baseline).
 */
@Slf4j
@Service
public class EvaluationService {

    public static final String DEFAULT_GOLDEN_SET = "eval/golden-set.json";
    static final String CORPUS_DIR = "eval/corpus/";

    /** Baseline first; then each technique alone and the key combinations. */
    public static final List<String> DEFAULT_CONFIGURATIONS = List.of(
            "semantic", "keyword", "hybrid",
            "semantic+mmr", "hybrid+mmr",
            "hybrid+rerank:llm", "hybrid+rerank:llm+mmr",
            "semantic+rewrite", "semantic+multi_query", "semantic+hyde", "hybrid+hyde");

    private final SearchService searchService;
    private final IngestionService ingestionService;
    private final RetrievalPlanner planner;
    private final JsonMapper json;

    public EvaluationService(SearchService searchService, IngestionService ingestionService,
                             RetrievalPlanner planner, JsonMapper json) {
        this.searchService = searchService;
        this.ingestionService = ingestionService;
        this.planner = planner;
        this.json = json;
    }

    public GoldenSet loadDefault() {
        try (InputStream in = new ClassPathResource(DEFAULT_GOLDEN_SET).getInputStream()) {
            return json.readValue(in, GoldenSet.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + DEFAULT_GOLDEN_SET, e);
        }
    }

    /** Default configurations, plus each external reranker that is configured on this server. */
    public List<EvalConfig> defaultConfigurations() {
        List<EvalConfig> configs = new ArrayList<>(DEFAULT_CONFIGURATIONS.stream().map(EvalConfig::parse).toList());
        for (RerankerType external : List.of(RerankerType.COHERE, RerankerType.CROSS_ENCODER)) {
            if (planner.unavailableReason(external).isEmpty()) {
                configs.add(EvalConfig.parse("hybrid+rerank:" + external.name()));
            }
        }
        return configs;
    }

    /** Ingests the golden set's corpus files; already-present files are reported as duplicates and skipped. */
    public int seedCorpus(GoldenSet set) {
        int added = 0;
        for (GoldenSet.CorpusFile file : set.corpus()) {
            byte[] bytes = read(CORPUS_DIR + file.file());
            IngestionRequest request = IngestionRequest.of(IntakeChannel.MULTIPART, file.file(), SourceFormat.MARKDOWN,
                    new NamedByteArrayResource(bytes, file.file()), file.categories(), Map.of())
                    .withSubmittedBy("evaluation");
            IngestionResult result = ingestionService.ingest(request);
            if (result.status() == IngestionStatus.INGESTED) {
                added++;
            }
        }
        return added;
    }

    public EvaluationReport evaluate(GoldenSet set, List<EvalConfig> configs, int k, boolean details, int seeded) {
        if (configs.isEmpty()) {
            throw new IllegalArgumentException("At least one configuration is required");
        }
        List<EvaluationReport.ConfigResult> results = new ArrayList<>();
        EvaluationReport.ConfigResult baseline = null;
        for (EvalConfig config : configs) {
            EvaluationReport.ConfigResult r = run(set, config, k, details, baseline);
            if (baseline == null) {
                baseline = r;
            }
            results.add(r);
            log.info("Evaluated {}: hit@{}={} recall={} MRR={} p50={}ms", config.name(), k,
                    round(r.hitRate()), round(r.recall()), round(r.mrr()), r.latencyP50Ms());
        }
        EvaluationReport report = new EvaluationReport(set.name(), set.questions().size(), k,
                configs.get(0).name(), seeded, results);
        log.info("Evaluation report\n{}", report.toMarkdown());
        return report;
    }

    private EvaluationReport.ConfigResult run(GoldenSet set, EvalConfig config, int k, boolean details,
                                              EvaluationReport.@Nullable ConfigResult baseline) {
        double hits = 0;
        double recall = 0;
        double rr = 0;
        int errors = 0;
        int degraded = 0;
        int llmCalls = 0;
        List<Long> latencies = new ArrayList<>();
        List<EvaluationReport.QuestionResult> perQuestion = new ArrayList<>();
        RetrievalOptions options = config.options();

        for (GoldenSet.Question q : set.questions()) {
            long start = System.nanoTime();
            List<SearchHit> top = List.of();
            String error = null;
            try {
                SearchResult result = searchService.search(new SearchQuery(q.question(), Set.of(), CategoryScope.ANY,
                        k, null, options));
                top = result.hits();
                if (!result.warnings().isEmpty()) {
                    degraded++;
                }
                if (result.applied() != null) {
                    llmCalls += result.applied().llmCalls();
                }
            } catch (RuntimeException e) {
                errors++;
                error = e.getMessage();
            }
            long ms = (System.nanoTime() - start) / 1_000_000;
            latencies.add(ms);
            double h = RetrievalMetrics.hitAtK(top, q.relevant(), k);
            double rc = RetrievalMetrics.recallAtK(top, q.relevant(), k);
            double r = RetrievalMetrics.reciprocalRank(top, q.relevant(), k);
            hits += h;
            recall += rc;
            rr += r;
            if (details) {
                perQuestion.add(new EvaluationReport.QuestionResult(q.id(), h, rc, r, ms,
                        top.stream().map(hit -> hit.source() + "#" + hit.title()).toList(), error));
            }
        }
        int n = set.questions().size();
        double hitRate = hits / n;
        double meanRecall = recall / n;
        double mrr = rr / n;
        long p50 = RetrievalMetrics.percentile(latencies, 50);
        EvaluationReport.Delta delta = baseline == null ? null : new EvaluationReport.Delta(
                hitRate - baseline.hitRate(), meanRecall - baseline.recall(), mrr - baseline.mrr(),
                p50 - baseline.latencyP50Ms());
        return new EvaluationReport.ConfigResult(config.name(), hitRate, meanRecall, mrr,
                latencies.stream().mapToLong(Long::longValue).average().orElse(0),
                p50, RetrievalMetrics.percentile(latencies, 95), (double) llmCalls / n, errors, degraded, delta,
                details ? perQuestion : null);
    }

    private static byte[] read(String path) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + path, e);
        }
    }

    private static double round(double v) {
        return Math.round(v * 1000) / 1000.0;
    }
}
