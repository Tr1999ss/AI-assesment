package com.shk.generic.ingestion.category;

import com.shk.generic.category.CategoryDefinition;
import com.shk.generic.category.CategoryRegistry;
import com.shk.generic.category.InvalidCategoryException;
import com.shk.generic.ingestion.extract.ExtractedDocument;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Caller supplied categories: all must be active registry entries; the first listed is primary. */
@Component
public class SuppliedCategoryResolver implements CategoryResolver {

    private final CategoryRegistry registry;

    public SuppliedCategoryResolver(CategoryRegistry registry) {
        this.registry = registry;
    }

    @Override
    public CategoryResolution resolve(ExtractedDocument document) {
        List<String> requested = new ArrayList<>(document.request().requestedCategories());
        if (requested.isEmpty()) {
            throw new IllegalArgumentException("No categories supplied");
        }
        List<String> invalid = requested.stream()
                .filter(name -> registry.find(name).filter(CategoryDefinition::active).isEmpty())
                .toList();
        if (!invalid.isEmpty()) {
            throw new InvalidCategoryException(invalid,
                    registry.active().stream().map(CategoryDefinition::name).toList());
        }
        return new CategoryResolution(
                CategorySet.of(requested.get(0), requested.subList(1, requested.size())),
                CategorySource.CALLER);
    }
}
