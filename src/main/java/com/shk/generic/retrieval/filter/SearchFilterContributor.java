package com.shk.generic.retrieval.filter;

import com.shk.generic.retrieval.SearchQuery;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder.Op;

import java.util.Optional;

/**
 * Contributes one condition to a search's metadata filter. All contributions are ANDed.
 * Add a bean to support a new filter (by source, date, ...) without touching the search service.
 * Build conditions with the typed builder only: values are then escaped by the vector store,
 * so caller input can't inject filter syntax.
 */
public interface SearchFilterContributor {

    Optional<Op> contribute(SearchQuery query, FilterExpressionBuilder builder);
}
