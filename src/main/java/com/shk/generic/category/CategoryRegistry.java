package com.shk.generic.category;

import java.util.List;
import java.util.Optional;

/** Single source of truth for which categories exist. */
public interface CategoryRegistry {

    /** Active categories, ordered by name. */
    List<CategoryDefinition> active();

    /** Any category, active or not. */
    Optional<CategoryDefinition> find(String name);
}
