package com.shk.generic.retrieval.keyword;

import org.jspecify.annotations.Nullable;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.pgvector.PgVectorFilterExpressionConverter;

/**
 * Converts the search's metadata filter with the same converter PgVectorStore uses for the semantic leg,
 * so both legs filter identically. The converter JSON-serializes and quote-escapes every value.
 */
final class MetadataFilterSql {

    private MetadataFilterSql() {
    }

    /** @return {@code ""} or {@code " AND <condition>"} */
    static String andClause(Filter.@Nullable Expression filter) {
        if (filter == null) {
            return "";
        }
        return " AND " + new PgVectorFilterExpressionConverter().convertExpression(filter);
    }
}
