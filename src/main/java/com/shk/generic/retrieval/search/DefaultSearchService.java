package com.shk.generic.retrieval.search;

import com.shk.generic.category.CategoryDefinition;
import com.shk.generic.category.CategoryRegistry;
import com.shk.generic.category.InvalidCategoryException;
import com.shk.generic.retrieval.RetrievalProperties;
import com.shk.generic.retrieval.RetrievalRequestException;
import com.shk.generic.retrieval.SearchQuery;
import com.shk.generic.retrieval.SearchResult;
import com.shk.generic.retrieval.filter.SearchFilterFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Validates the query, applies defaults and limits, builds the metadata filter and searches.
 * Unknown categories are rejected; inactive ones are allowed, since documents already tagged
 * with a deactivated category are still stored and searchable.
 */
@Service
public class DefaultSearchService implements SearchService {

    private final ChunkSearcher searcher;
    private final SearchFilterFactory filterFactory;
    private final CategoryRegistry categories;
    private final RetrievalProperties properties;

    public DefaultSearchService(ChunkSearcher searcher, SearchFilterFactory filterFactory,
                                CategoryRegistry categories, RetrievalProperties properties) {
        this.searcher = searcher;
        this.filterFactory = filterFactory;
        this.categories = categories;
        this.properties = properties;
    }

    @Override
    public SearchResult search(SearchQuery query) {
        validateText(query.text());
        validateCategories(query);
        int topK = topK(query);
        double minScore = minScore(query);

        return new SearchResult(query.text(),
                searcher.search(query.text(), topK, minScore, filterFactory.filterFor(query).orElse(null)));
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

    private int topK(SearchQuery query) {
        if (query.topK() == null) {
            return properties.defaultTopK();
        }
        if (query.topK() < 1 || query.topK() > properties.maxTopK()) {
            throw new RetrievalRequestException("topK must be between 1 and " + properties.maxTopK());
        }
        return query.topK();
    }

    private double minScore(SearchQuery query) {
        if (query.minScore() == null) {
            return properties.defaultMinScore();
        }
        if (query.minScore() < 0.0 || query.minScore() > 1.0) {
            throw new RetrievalRequestException("minScore must be between 0 and 1");
        }
        return query.minScore();
    }
}
