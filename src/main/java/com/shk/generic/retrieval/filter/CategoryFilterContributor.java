package com.shk.generic.retrieval.filter;

import com.shk.generic.ingestion.chunk.ChunkMetadata;
import com.shk.generic.retrieval.CategoryScope;
import com.shk.generic.retrieval.SearchQuery;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder.Op;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * {@link CategoryScope#ANY}: {@code categories IN [...]}. The metadata value is a JSON array, and the
 * pgvector filter matches if any element matches, so this finds primary and overlap tags alike.
 * {@link CategoryScope#PRIMARY}: {@code primary_category IN [...]}.
 */
@Component
public class CategoryFilterContributor implements SearchFilterContributor {

    @Override
    public Optional<Op> contribute(SearchQuery query, FilterExpressionBuilder builder) {
        if (query.categories().isEmpty()) {
            return Optional.empty();
        }
        String key = query.scope() == CategoryScope.PRIMARY ? ChunkMetadata.PRIMARY_CATEGORY : ChunkMetadata.CATEGORIES;
        List<Object> values = new ArrayList<>(query.categories());
        return Optional.of(builder.in(key, values));
    }
}
