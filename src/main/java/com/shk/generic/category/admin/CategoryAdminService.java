package com.shk.generic.category.admin;

import com.shk.generic.category.CategoryDefinition;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Use cases for managing categories. Names are immutable: they are stored on every chunk,
 * so renaming would mean re-tagging. Create a new category and deactivate the old one instead.
 */
public interface CategoryAdminService {

    List<CategoryDefinition> list(boolean includeInactive);

    CategoryDefinition get(String name);

    CategoryDefinition create(String name, String description);

    CategoryDefinition update(String name, @Nullable String description, @Nullable Boolean active);

    /** Only unused categories can be deleted; deactivate a category that documents still use. */
    void delete(String name);
}
