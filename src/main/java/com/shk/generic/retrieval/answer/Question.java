package com.shk.generic.retrieval.answer;

import com.shk.generic.retrieval.CategoryScope;
import com.shk.generic.retrieval.SearchQuery;
import org.jspecify.annotations.Nullable;

import java.util.Set;

/** A question to answer from stored documents, optionally restricted to categories. */
public record Question(String text, Set<String> categories, CategoryScope scope, @Nullable Integer topK) {

    public SearchQuery toSearchQuery() {
        return new SearchQuery(text, categories, scope, topK, null);
    }
}
