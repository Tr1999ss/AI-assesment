package com.shk.generic.retrieval.web;

import com.shk.generic.retrieval.CategoryScope;
import com.shk.generic.retrieval.answer.Question;
import com.shk.generic.retrieval.options.RetrievalOptions;
import jakarta.validation.constraints.NotBlank;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * @param categories     restrict sources to these categories; omit to search everything
 * @param scope          ANY (default): tagged with any of them; PRIMARY: primary category is one of them
 * @param mode           SEMANTIC | KEYWORD | HYBRID (case-insensitive); omit for the configured default
 * @param queryTransform NONE | REWRITE | MULTI_QUERY | HYDE
 * @param rerank         NONE | LLM | COHERE | CROSS_ENCODER
 * @param diversify      NONE | MMR
 */
public record AskRequest(
        @NotBlank String question,
        @Nullable List<String> categories,
        @Nullable CategoryScope scope,
        @Nullable Integer topK,
        @Nullable String mode,
        @Nullable String queryTransform,
        @Nullable String rerank,
        @Nullable String diversify,
        @Nullable Integer candidates,
        @Nullable Double mmrLambda,
        @Nullable Boolean explain
) {
    public Question toQuestion() {
        Set<String> cats = categories == null ? Set.of() : new LinkedHashSet<>(categories);
        return new Question(question, cats, scope == null ? CategoryScope.ANY : scope, topK,
                RetrievalOptions.parse(mode, queryTransform, rerank, diversify, candidates, mmrLambda, explain));
    }
}
