package com.shk.generic.category.admin;

import com.shk.generic.category.CategoryDefinition;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/** Authoritative, uncached access to category definitions, including writes. */
public interface CategoryStore {

    List<CategoryDefinition> findAll(boolean includeInactive);

    Optional<CategoryDefinition> find(String name);

    /** @return false if a category with that name already exists */
    boolean insert(CategoryDefinition category);

    /** Changes only the non-null fields. Empty if the category doesn't exist. */
    Optional<CategoryDefinition> update(String name, @Nullable String description, @Nullable Boolean active);

    /** @return false if the category didn't exist */
    boolean delete(String name);
}
