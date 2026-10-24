package com.shk.generic.ingestion.category.classifier;

import com.shk.generic.ingestion.category.CategoryProperties;
import com.shk.generic.ingestion.category.CategoryResolution;
import com.shk.generic.ingestion.category.CategorySource;
import com.shk.generic.ingestion.category.classifier.Classification.Overlap;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClassificationGuardTest {

    private static final Set<String> ACTIVE = Set.of("hr", "finance", "legal", "engineering", "general");

    private final ClassificationGuard guard = new ClassificationGuard(
            new CategoryProperties("general", 0.5, 0.6, 2, 3000));

    @Test
    void confidentPickIsKeptWithOverlapsStrongestFirst() {
        CategoryResolution r = guard.interpret(new Classification("hr", 0.9,
                List.of(new Overlap("legal", 0.7), new Overlap("finance", 0.95))), ACTIVE);

        assertThat(r.source()).isEqualTo(CategorySource.AI);
        assertThat(r.categories().all()).containsExactly("hr", "finance", "legal");
    }

    @Test
    void inventedCategoriesAreDropped() {
        CategoryResolution r = guard.interpret(new Classification("hr", 0.9,
                List.of(new Overlap("payroll", 0.99), new Overlap("legal", 0.8))), ACTIVE);

        assertThat(r.categories().all()).containsExactly("hr", "legal");
    }

    @Test
    void namesAreNormalizedBeforeMatching() {
        CategoryResolution r = guard.interpret(new Classification("  HR ", 0.9, null), ACTIVE);

        assertThat(r.categories().primary()).isEqualTo("hr");
    }

    @Test
    void lowConfidencePrimaryFallsBack() {
        CategoryResolution r = guard.interpret(new Classification("hr", 0.3, List.of()), ACTIVE);

        assertThat(r.source()).isEqualTo(CategorySource.FALLBACK);
        assertThat(r.categories().all()).containsExactly("general");
    }

    @Test
    void invalidPrimaryIsReplacedByStrongestConfidentOverlap() {
        CategoryResolution r = guard.interpret(new Classification("payroll", 0.99,
                List.of(new Overlap("finance", 0.8), new Overlap("legal", 0.65))), ACTIVE);

        assertThat(r.source()).isEqualTo(CategorySource.AI);
        assertThat(r.categories().all()).containsExactly("finance", "legal");
    }

    @Test
    void weakOverlapsAreDroppedAndCapped() {
        CategoryResolution r = guard.interpret(new Classification("engineering", 0.9, List.of(
                new Overlap("hr", 0.61), new Overlap("finance", 0.9), new Overlap("legal", 0.8),
                new Overlap("general", 0.99), new Overlap("engineering", 0.99), new Overlap(null, 1.0),
                new Overlap("hr", 0.59))), ACTIVE);

        // max-overlaps = 2; primary, fallback and below-threshold entries never appear as overlaps
        assertThat(r.categories().all()).containsExactly("engineering", "finance", "legal");
    }

    @Test
    void nullOrEmptyOutputFallsBack() {
        assertThat(guard.interpret(null, ACTIVE).source()).isEqualTo(CategorySource.FALLBACK);
        assertThat(guard.interpret(new Classification(null, null, null), ACTIVE).categories().primary())
                .isEqualTo("general");
    }

    @Test
    void outOfRangeConfidenceIsClamped() {
        CategoryResolution r = guard.interpret(new Classification("legal", 7.0,
                List.of(new Overlap("hr", -3.0))), ACTIVE);

        assertThat(r.categories().all()).containsExactly("legal");
    }

    @Test
    void missingFallbackCategoryIsAConfigurationError() {
        assertThatThrownBy(() -> guard.interpret(new Classification("hr", 0.9, null), Set.of("hr")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("general");
    }
}
