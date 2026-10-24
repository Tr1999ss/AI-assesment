package com.shk.generic.category.web;

import com.shk.generic.category.CategoryNames;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** @param description read by the AI classifier: say what belongs in the category, not just its name */
public record CreateCategoryRequest(
        @NotBlank @Size(max = CategoryNames.MAX_LENGTH)
        @Pattern(regexp = CategoryNames.PATTERN, message = "lowercase letters, digits, '-' and '_' only")
        String name,
        @NotBlank @Size(max = 1000) String description
) {
}
