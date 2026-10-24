package com.shk.generic.retrieval.filter;

import com.shk.generic.retrieval.SearchQuery;
import org.springframework.ai.vectorstore.filter.Filter;

import java.util.Optional;

/** Turns a query into a vector store metadata filter; empty means no filtering. */
public interface SearchFilterFactory {

    Optional<Filter.Expression> filterFor(SearchQuery query);
}
