package com.shk.generic.retrieval.answer;

import com.shk.generic.retrieval.CategoryScope;
import com.shk.generic.retrieval.SearchQuery;
import com.shk.generic.retrieval.options.RetrievalOptions;
import org.jspecify.annotations.Nullable;

import java.util.Set;

/** A question to answer from stored documents, optionally restricted to categories, with retrieval options. */
public record Question(String text, Set<String> categories, CategoryScope scope, @Nullable Integer topK,
                       RetrievalOptions options) {

    public Question {
        options = options == null ? RetrievalOptions.DEFAULTS : options;
    }

    public Question(String text, Set<String> categories, CategoryScope scope, @Nullable Integer topK) {
        this(text, categories, scope, topK, RetrievalOptions.DEFAULTS);
    }

    public SearchQuery toSearchQuery() {
        return new SearchQuery(text, categories, scope, topK, null, options);
    }
}
