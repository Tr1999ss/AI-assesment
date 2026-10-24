package com.shk.generic.ingestion.category;

import com.shk.generic.category.admin.ProtectedCategoryPolicy;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** The classifier's fallback category must always exist and be active. */
@Component
public class FallbackCategoryProtection implements ProtectedCategoryPolicy {

    private final CategoryProperties properties;

    public FallbackCategoryProtection(CategoryProperties properties) {
        this.properties = properties;
    }

    @Override
    public Optional<String> protectionReason(String category) {
        return category.equals(properties.fallback())
                ? Optional.of("it is the classifier fallback (ingestion.category.fallback)")
                : Optional.empty();
    }
}
