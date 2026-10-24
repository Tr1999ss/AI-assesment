package com.shk.generic.category.web;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/** Partial update: omitted fields are left unchanged. */
public record UpdateCategoryRequest(
        @Nullable @Size(max = 1000) @Pattern(regexp = ".*\\S.*", message = "must not be blank") String description,
        @Nullable Boolean active
) {
    @AssertTrue(message = "provide 'description' and/or 'active'")
    public boolean isNotEmpty() {
        return description != null || active != null;
    }
}
