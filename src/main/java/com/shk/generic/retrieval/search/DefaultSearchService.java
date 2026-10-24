package com.shk.generic.retrieval.search;

import com.shk.generic.category.CategoryDefinition;
import com.shk.generic.category.CategoryRegistry;
import com.shk.generic.category.InvalidCategoryException;
import com.shk.generic.retrieval.RetrievalProperties;
import com.shk.generic.retrieval.RetrievalRequestException;
import com.shk.generic.retrieval.SearchQuery;
import com.shk.generic.retrieval.SearchResult;
import com.shk.generic.retrieval.filter.SearchFilterFactory;
import com.shk.generic.retrieval.options.RetrievalPlanner;
import com.shk.generic.retrieval.pipeline.RetrievalPipeline;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Validates the query, resolves the retrieval plan (defaults, limits, availability), builds the metadata
 * filter once, and runs the pipeline. Unknown categories are rejected; inactive ones are allowed, since
 * documents already tagged with a deactivated category are still stored and searchable.
 */
@Service
public class DefaultSearchService implements SearchService {

    private final RetrievalPipeline pipeline;
    private final RetrievalPlanner planner;
    private final SearchFilterFactory filterFactory;
    private final CategoryRegistry categories;
    private final RetrievalProperties properties;

    public DefaultSearchService(RetrievalPipeline pipeline, RetrievalPlanner planner, SearchFilterFactory filterFactory,
                                CategoryRegistry categories, RetrievalProperties properties) {
        this.pipeline = pipeline;
        this.planner = planner;
        this.filterFactory = filterFactory;
        this.categories = categories;
        this.properties = properties;
    }

    @Override
    public SearchResult search(SearchQuery query) {
        validateText(query.text());
        validateCategories(query);
        return pipeline.run(planner.plan(query), filterFactory.filterFor(query).orElse(null));
    }

    private void validateText(String text) {
        if (text.isEmpty()) {
            throw new RetrievalRequestException("Query must not be blank");
        }
        if (text.length() > properties.maxQueryChars()) {
            throw new RetrievalRequestException("Query is longer than " + properties.maxQueryChars() + " characters");
        }
    }

    private void validateCategories(SearchQuery query) {
        List<String> unknown = query.categories().stream()
                .filter(name -> categories.find(name).isEmpty())
                .toList();
        if (!unknown.isEmpty()) {
            throw new InvalidCategoryException(unknown,
                    categories.active().stream().map(CategoryDefinition::name).toList());
        }
    }
}
