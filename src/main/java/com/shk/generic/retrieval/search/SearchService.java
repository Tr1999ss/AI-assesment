package com.shk.generic.retrieval.search;

import com.shk.generic.retrieval.SearchQuery;
import com.shk.generic.retrieval.SearchResult;

/** Semantic search over ingested documents. Every entry point (REST, MCP, answering) goes through here. */
public interface SearchService {

    SearchResult search(SearchQuery query);
}
