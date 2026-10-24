package com.shk.generic.retrieval.web;

import com.shk.generic.retrieval.CategoryScope;
import com.shk.generic.retrieval.answer.Question;
import jakarta.validation.constraints.NotBlank;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * @param categories restrict sources to these categories; omit to search everything
 * @param scope      ANY (default): tagged with any of them; PRIMARY: primary category is one of them
 */
public record AskRequest(
        @NotBlank String question,
        @Nullable List<String> categories,
        @Nullable CategoryScope scope,
        @Nullable Integer topK
) {
    public Question toQuestion() {
        Set<String> cats = categories == null ? Set.of() : new LinkedHashSet<>(categories);
        return new Question(question, cats, scope == null ? CategoryScope.ANY : scope, topK);
    }
}
