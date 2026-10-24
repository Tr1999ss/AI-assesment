package com.shk.generic.retrieval.search;

import com.shk.generic.retrieval.SearchHit;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.vectorstore.filter.Filter;

import java.util.List;

/** Low-level similarity search over stored chunks. Parameters are already validated. */
public interface ChunkSearcher {

    List<SearchHit> search(String text, int topK, double minScore, Filter.@Nullable Expression filter);
}
