package com.shk.generic.retrieval.web;

import com.shk.generic.retrieval.CategoryScope;
import com.shk.generic.retrieval.SearchQuery;
import com.shk.generic.retrieval.SearchResult;
import com.shk.generic.retrieval.answer.Answer;
import com.shk.generic.retrieval.answer.AnswerService;
import com.shk.generic.retrieval.options.RetrievalCatalog;
import com.shk.generic.retrieval.options.RetrievalOptions;
import com.shk.generic.retrieval.search.SearchService;
import jakarta.validation.Valid;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashSet;
import java.util.List;

@RestController
@RequestMapping("/api")
public class RetrievalController {

    private final SearchService searchService;
    private final AnswerService answerService;
    private final RetrievalCatalog catalog;

    public RetrievalController(SearchService searchService, AnswerService answerService, RetrievalCatalog catalog) {
        this.searchService = searchService;
        this.answerService = answerService;
        this.catalog = catalog;
    }

    /**
     * {@code GET /api/search?q=leave&categories=hr&mode=hybrid&rerank=llm&diversify=mmr&explain=true}.
     * Option values are case-insensitive; '-' and '_' are interchangeable (multi-query = MULTI_QUERY).
     */
    @GetMapping("/search")
    public SearchResult search(@RequestParam("q") String query,
                               @RequestParam(required = false) @Nullable List<String> categories,
                               @RequestParam(defaultValue = "ANY") CategoryScope scope,
                               @RequestParam(required = false) @Nullable Integer topK,
                               @RequestParam(required = false) @Nullable Double minScore,
                               @RequestParam(required = false) @Nullable String mode,
                               @RequestParam(required = false) @Nullable String queryTransform,
                               @RequestParam(required = false) @Nullable String rerank,
                               @RequestParam(required = false) @Nullable String diversify,
                               @RequestParam(required = false) @Nullable Integer candidates,
                               @RequestParam(required = false) @Nullable Double mmrLambda,
                               @RequestParam(required = false) @Nullable Boolean explain) {
        RetrievalOptions options = RetrievalOptions.parse(mode, queryTransform, rerank, diversify, candidates,
                mmrLambda, explain);
        return searchService.search(new SearchQuery(query,
                categories == null ? null : new LinkedHashSet<>(categories), scope, topK, minScore, options));
    }

    /** Every retrieval option, whether it's enabled here, defaults, limits, and cost notes. */
    @GetMapping("/search/modes")
    public RetrievalCatalog.Catalog modes() {
        return catalog.describe();
    }

    @PostMapping(path = "/ask", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Answer ask(@Valid @RequestBody AskRequest request) {
        return answerService.ask(request.toQuestion());
    }
}
