package com.shk.generic.category.web;

import com.shk.generic.category.CategoryDefinition;

public record CategoryResponse(String name, String description, boolean active) {

    public static CategoryResponse from(CategoryDefinition category) {
        return new CategoryResponse(category.name(), category.description(), category.active());
    }
}
