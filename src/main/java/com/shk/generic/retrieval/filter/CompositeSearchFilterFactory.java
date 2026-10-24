package com.shk.generic.retrieval.filter;

import com.shk.generic.retrieval.SearchQuery;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder.Op;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/** ANDs together whatever every {@link SearchFilterContributor} bean contributes. */
@Component
public class CompositeSearchFilterFactory implements SearchFilterFactory {

    private final List<SearchFilterContributor> contributors;

    public CompositeSearchFilterFactory(List<SearchFilterContributor> contributors) {
        this.contributors = List.copyOf(contributors);
    }

    @Override
    public Optional<Filter.Expression> filterFor(SearchQuery query) {
        FilterExpressionBuilder builder = new FilterExpressionBuilder();
        return contributors.stream()
                .map(c -> c.contribute(query, builder))
                .flatMap(Optional::stream)
                .reduce(builder::and)
                .map(Op::build);
    }
}
