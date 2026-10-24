package com.shk.generic.retrieval.options;

import com.shk.generic.retrieval.RetrievalProperties;
import com.shk.generic.retrieval.diversify.MmrProperties;
import com.shk.generic.retrieval.fusion.FusionProperties;
import com.shk.generic.retrieval.keyword.ActiveKeywordEngine;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/** Everything a caller can choose, whether it is usable right now, defaults and limits. Backs GET /api/search/modes. */
@Component
public class RetrievalCatalog {

    private final RetrievalProperties basics;
    private final RetrievalDefaultsProperties defaults;
    private final FusionProperties fusion;
    private final MmrProperties mmr;
    private final ActiveKeywordEngine keyword;
    private final RetrievalPlanner planner;

    public RetrievalCatalog(RetrievalProperties basics, RetrievalDefaultsProperties defaults, FusionProperties fusion,
                            MmrProperties mmr, ActiveKeywordEngine keyword, RetrievalPlanner planner) {
        this.basics = basics;
        this.defaults = defaults;
        this.fusion = fusion;
        this.mmr = mmr;
        this.keyword = keyword;
        this.planner = planner;
    }

    public Catalog describe() {
        return new Catalog(
                new Defaults(defaults.defaultMode(), defaults.defaultQueryTransform(), defaults.defaultRerank(),
                        defaults.defaultDiversify(), basics.defaultTopK(), defaults.defaultCandidates(),
                        basics.defaultMinScore(), mmr.lambda(), fusion.rrfK()),
                new Limits(basics.maxTopK(), defaults.maxCandidates(), defaults.maxLlmCallsPerRequest(),
                        defaults.llmTimeout().toSeconds(), basics.maxQueryChars()),
                keyword.get().type().name(),
                options(RetrievalMode.class),
                options(QueryTransformType.class),
                Arrays.stream(RerankerType.values())
                        .map(t -> option(t, planner.unavailableReason(t).orElse(null)))
                        .toList(),
                options(DiversifierType.class));
    }

    private static <E extends Enum<E> & RetrievalOption> List<Option> options(Class<E> type) {
        return Arrays.stream(type.getEnumConstants()).map(e -> option(e, null)).toList();
    }

    private static Option option(RetrievalOption o, @Nullable String unavailableReason) {
        return new Option(o.name(), unavailableReason == null, unavailableReason, o.description(), o.costNote());
    }

    public record Catalog(Defaults defaults, Limits limits, String keywordEngine, List<Option> mode,
                          List<Option> queryTransform, List<Option> rerank, List<Option> diversify) {
    }

    public record Defaults(RetrievalMode mode, QueryTransformType queryTransform, RerankerType rerank,
                           DiversifierType diversify, int topK, int candidatesWhenPooling, double minScore,
                           double mmrLambda, int rrfK) {
    }

    public record Limits(int maxTopK, int maxCandidates, int maxLlmCallsPerRequest, long llmTimeoutSeconds,
                         int maxQueryChars) {
    }

    /** @param unavailableReason why it can't be used now (e.g. a missing API key); null when enabled */
    public record Option(String value, boolean enabled, @Nullable String unavailableReason, String description,
                         String cost) {
    }
}
