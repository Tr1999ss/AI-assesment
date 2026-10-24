package com.shk.generic.retrieval.keyword;

import com.shk.generic.retrieval.SearchHit;
import com.shk.generic.retrieval.pipeline.ScoreType;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.vectorstore.filter.Filter;

import java.util.List;

/**
 * Lexical retrieval over the same chunks the vector store holds. Implementations must apply the metadata
 * {@code filter} with exactly the semantics of the semantic leg (see {@link MetadataFilterSql}).
 */
public interface KeywordEngine {

    KeywordProperties.KeywordEngineType type();

    ScoreType scoreType();

    /** Throws with setup instructions if the engine can't work against the current database. */
    default void verify() {
    }

    /**
     * @param minScore floor in this engine's own scale
     * @return matching chunks, best first; only chunks that actually match the query terms
     */
    List<SearchHit> search(String text, int limit, double minScore, Filter.@Nullable Expression filter);
}
