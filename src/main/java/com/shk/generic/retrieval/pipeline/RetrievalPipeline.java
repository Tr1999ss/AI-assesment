package com.shk.generic.retrieval.pipeline;

import com.shk.generic.retrieval.AppliedRetrieval;
import com.shk.generic.retrieval.RetrievalExplanation;
import com.shk.generic.retrieval.RetrievalExplanation.Stage;
import com.shk.generic.retrieval.RetrievalUnavailableException;
import com.shk.generic.retrieval.SearchHit;
import com.shk.generic.retrieval.SearchResult;
import com.shk.generic.retrieval.diversify.Diversifier;
import com.shk.generic.retrieval.fusion.ReciprocalRankFusion;
import com.shk.generic.retrieval.keyword.ActiveKeywordEngine;
import com.shk.generic.retrieval.keyword.KeywordEngine;
import com.shk.generic.retrieval.llm.LlmCallBudget;
import com.shk.generic.retrieval.llm.LlmStageException;
import com.shk.generic.retrieval.options.DiversifierType;
import com.shk.generic.retrieval.options.QueryTransformType;
import com.shk.generic.retrieval.options.RerankerType;
import com.shk.generic.retrieval.options.RetrievalMode;
import com.shk.generic.retrieval.options.RetrievalPlan;
import com.shk.generic.retrieval.rerank.RerankException;
import com.shk.generic.retrieval.rerank.Reranker;
import com.shk.generic.retrieval.rerank.RerankerRegistry;
import com.shk.generic.retrieval.search.ChunkSearcher;
import com.shk.generic.retrieval.transform.QueryTransformer;
import com.shk.generic.retrieval.transform.TransformedQuery;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

/**
 * The only class that knows stage order:
 * <pre>query transform → retrieval legs (parallel) → RRF fusion → rerank → diversify (MMR) → top K</pre>
 * Each stage is an interface with a no-op default. Legs apply their own relevance floor (cosine for
 * semantic, the engine's rank for keyword); later stages only reorder candidates that passed a floor,
 * so an empty result means "nothing relevant" in every mode.
 * <p>
 * Optional LLM/HTTP stages (transform, rerank) degrade instead of failing the search: the previous
 * result is kept, {@code applied} reports the stage as NONE, and {@code warnings} says why.
 * A retrieval leg failing is a real outage and surfaces as 503.
 */
@Slf4j
@Component
public class RetrievalPipeline {

    private final ChunkSearcher semantic;
    private final ActiveKeywordEngine keyword;
    private final Strategies<QueryTransformType, QueryTransformer> transformers;
    private final ReciprocalRankFusion fusion;
    private final RerankerRegistry rerankers;
    private final Strategies<DiversifierType, Diversifier> diversifiers;
    private final AsyncTaskExecutor executor;

    public RetrievalPipeline(ChunkSearcher semantic, ActiveKeywordEngine keyword, List<QueryTransformer> transformers,
                             ReciprocalRankFusion fusion, RerankerRegistry rerankers, List<Diversifier> diversifiers,
                             @Qualifier("retrievalExecutor") AsyncTaskExecutor executor) {
        this.semantic = semantic;
        this.keyword = keyword;
        this.transformers = new Strategies<>(QueryTransformType.class, transformers, QueryTransformer::type);
        this.fusion = fusion;
        this.rerankers = rerankers;
        this.diversifiers = new Strategies<>(DiversifierType.class, diversifiers, Diversifier::type);
        this.executor = executor;
    }

    public SearchResult run(RetrievalPlan plan, Filter.@Nullable Expression filter) {
        LlmCallBudget budget = new LlmCallBudget(plan.maxLlmCalls());
        List<String> warnings = new ArrayList<>();
        List<Stage> stages = new ArrayList<>();
        KeywordEngine keywordEngine = keyword.get();

        // 1. Query transform (optional, degrades to the original query)
        QueryTransformType appliedTransform = plan.queryTransform();
        TransformedQuery tq;
        long t = System.nanoTime();
        try {
            tq = transformers.get(plan.queryTransform()).transform(plan.text(), budget);
        } catch (LlmStageException e) {
            warnings.add("queryTransform " + plan.queryTransform() + " skipped: " + e.getMessage());
            appliedTransform = QueryTransformType.NONE;
            tq = TransformedQuery.unchanged(plan.text());
        }
        if (plan.queryTransform() != QueryTransformType.NONE) {
            stages.add(new Stage("transform:" + plan.queryTransform(), 0, ms(t), tq.detail()));
        }

        // 2. Retrieval legs, in parallel; every leg gets the same metadata filter
        List<String> semanticQueries = plan.mode().usesSemantic() ? tq.semanticQueries() : List.of();
        List<String> keywordQueries = plan.mode().usesKeyword() ? tq.keywordQueries() : List.of();
        List<LegTask> legs = new ArrayList<>();
        for (int i = 0; i < semanticQueries.size(); i++) {
            String q = semanticQueries.get(i);
            legs.add(new LegTask("semantic[" + i + "]", q,
                    () -> semantic.search(q, plan.candidates(), plan.minScore(), filter), ScoreType.COSINE));
        }
        for (int i = 0; i < keywordQueries.size(); i++) {
            String q = keywordQueries.get(i);
            legs.add(new LegTask("keyword[" + i + "]", q,
                    () -> keywordEngine.search(q, plan.candidates(), plan.keywordMinScore(), filter),
                    keywordEngine.scoreType()));
        }
        List<RankedList> lists = runLegs(legs, stages);

        // 3. Fusion: only when there is more than one list, so single-leg scores keep their native meaning
        List<Candidate> pool;
        if (lists.size() == 1) {
            pool = lists.get(0).candidates();
        } else {
            t = System.nanoTime();
            pool = fusion.fuse(lists);
            stages.add(new Stage(ReciprocalRankFusion.STAGE, pool.size(), ms(t), lists.size() + " lists, k=" + fusion.k()));
        }
        pool = pool.subList(0, Math.min(plan.candidates(), pool.size()));

        // 4. Rerank (optional, degrades to the fused order)
        RerankerType appliedRerank = plan.rerank();
        if (plan.rerank() != RerankerType.NONE && !pool.isEmpty()) {
            Reranker reranker = rerankers.get(plan.rerank());
            t = System.nanoTime();
            try {
                pool = reranker.rerank(plan.text(), pool, budget);
                stages.add(new Stage(reranker.stage(), pool.size(), ms(t), "query: original"));
            } catch (RerankException | LlmStageException e) {
                warnings.add("rerank " + plan.rerank() + " skipped: " + e.getMessage());
                appliedRerank = RerankerType.NONE;
            }
        }

        // 5. Diversify / select top K
        t = System.nanoTime();
        List<Candidate> top = diversifiers.get(plan.diversify()).select(pool, plan.topK(), plan.mmrLambda());
        if (plan.diversify() != DiversifierType.NONE) {
            stages.add(new Stage("mmr", top.size(), ms(t), "lambda=" + plan.mmrLambda()));
        }

        List<SearchHit> hits = top.stream().map(c -> c.toHit(plan.explain())).toList();
        ScoreType scoreType = scoreType(lists.size(), appliedRerank, plan, keywordEngine);
        AppliedRetrieval applied = new AppliedRetrieval(plan.mode(), appliedTransform, appliedRerank, plan.diversify(),
                plan.topK(), plan.candidates(),
                plan.mode().usesKeyword() ? keywordEngine.type().name() : null,
                plan.mode().usesSemantic() ? plan.minScore() : null,
                plan.mode().usesKeyword() ? plan.keywordMinScore() : null,
                lists.size() > 1 ? fusion.k() : null,
                plan.diversify() == DiversifierType.MMR ? plan.mmrLambda() : null,
                budget.used(), scoreType);
        RetrievalExplanation explanation = plan.explain()
                ? new RetrievalExplanation(plan.text(), semanticQueries, keywordQueries, stages)
                : null;
        warnings.forEach(w -> log.warn("Retrieval degraded: {}", w));
        return new SearchResult(plan.text(), hits, applied, warnings, explanation);
    }

    private List<RankedList> runLegs(List<LegTask> legs, List<Stage> stages) {
        List<CompletableFuture<RankedList>> futures = new ArrayList<>();
        List<long[]> durations = new ArrayList<>();
        try {
            for (LegTask leg : legs) {
                long[] duration = new long[1];
                durations.add(duration);
                futures.add(CompletableFuture.supplyAsync(() -> {
                    long start = System.nanoTime();
                    List<SearchHit> hits = leg.search().get();
                    List<Candidate> ranked = new ArrayList<>(hits.size());
                    for (int i = 0; i < hits.size(); i++) {
                        SearchHit h = hits.get(i);
                        ranked.add(Candidate.first(h, leg.name(), i + 1, h.score(), leg.scoreType()));
                    }
                    duration[0] = ms(start);
                    return new RankedList(leg.name(), ranked);
                }, executor));
            }
            List<RankedList> lists = futures.stream().map(CompletableFuture::join).toList();
            for (int i = 0; i < legs.size(); i++) {
                stages.add(new Stage(legs.get(i).name(), lists.get(i).candidates().size(), durations.get(i)[0],
                        "query: " + abbreviate(legs.get(i).query())));
            }
            return lists;
        } catch (TaskRejectedException e) {
            throw new RetrievalUnavailableException("Search capacity exhausted", e);
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException re) {
                throw re;
            }
            throw new RetrievalUnavailableException("Search failed", e);
        }
    }

    private static ScoreType scoreType(int lists, RerankerType rerank, RetrievalPlan plan, KeywordEngine engine) {
        if (rerank != RerankerType.NONE) {
            return ScoreType.RERANK;
        }
        if (lists > 1) {
            return ScoreType.RRF;
        }
        return plan.mode() == RetrievalMode.KEYWORD ? engine.scoreType() : ScoreType.COSINE;
    }

    private static String abbreviate(String s) {
        return s.length() <= 120 ? s : s.substring(0, 117) + "...";
    }

    private static long ms(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private record LegTask(String name, String query, Supplier<List<SearchHit>> search, ScoreType scoreType) {
    }
}
