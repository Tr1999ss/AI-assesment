package com.shk.generic.retrieval.web;

import com.shk.generic.retrieval.CategoryScope;
import com.shk.generic.retrieval.SearchQuery;
import com.shk.generic.retrieval.SearchResult;
import com.shk.generic.retrieval.answer.Answer;
import com.shk.generic.retrieval.answer.AnswerService;
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

    public RetrievalController(SearchService searchService, AnswerService answerService) {
        this.searchService = searchService;
        this.answerService = answerService;
    }

    /** {@code GET /api/search?q=leave&categories=hr&categories=legal&scope=ANY&topK=5&minScore=0.3} */
    @GetMapping("/search")
    public SearchResult search(@RequestParam("q") String query,
                               @RequestParam(required = false) @Nullable List<String> categories,
                               @RequestParam(defaultValue = "ANY") CategoryScope scope,
                               @RequestParam(required = false) @Nullable Integer topK,
                               @RequestParam(required = false) @Nullable Double minScore) {
        return searchService.search(new SearchQuery(query,
                categories == null ? null : new LinkedHashSet<>(categories), scope, topK, minScore));
    }

    @PostMapping(path = "/ask", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Answer ask(@Valid @RequestBody AskRequest request) {
        return answerService.ask(request.toQuestion());
    }
}
