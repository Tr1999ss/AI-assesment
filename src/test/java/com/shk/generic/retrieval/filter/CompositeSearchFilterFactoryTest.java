package com.shk.generic.retrieval.filter;

import com.shk.generic.retrieval.CategoryScope;
import com.shk.generic.retrieval.SearchQuery;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CompositeSearchFilterFactoryTest {

    private final FilterExpressionBuilder b = new FilterExpressionBuilder();

    @Test
    void noCategoriesMeansNoFilter() {
        var factory = new CompositeSearchFilterFactory(List.of(new CategoryFilterContributor()));

        assertThat(factory.filterFor(SearchQuery.of("q", List.of()))).isEmpty();
    }

    @Test
    void anyScopeFiltersOnTheCategoriesArray() {
        var factory = new CompositeSearchFilterFactory(List.of(new CategoryFilterContributor()));

        assertThat(factory.filterFor(SearchQuery.of("q", List.of("HR", "legal"))))
                .contains(b.in("categories", List.<Object>of("hr", "legal")).build());
    }

    @Test
    void primaryScopeFiltersOnThePrimaryCategory() {
        var factory = new CompositeSearchFilterFactory(List.of(new CategoryFilterContributor()));
        var query = new SearchQuery("q", Set.of("hr"), CategoryScope.PRIMARY, null, null);

        assertThat(factory.filterFor(query)).contains(b.in("primary_category", List.<Object>of("hr")).build());
    }

    @Test
    void contributionsAreAndedTogether() {
        SearchFilterContributor sourceIsPdf = (q, builder) -> Optional.of(builder.eq("format", "PDF"));
        var factory = new CompositeSearchFilterFactory(List.of(new CategoryFilterContributor(), sourceIsPdf));

        Filter.Expression expected = b.and(b.in("categories", List.<Object>of("hr")), b.eq("format", "PDF")).build();
        assertThat(factory.filterFor(SearchQuery.of("q", List.of("hr")))).contains(expected);
    }
}
