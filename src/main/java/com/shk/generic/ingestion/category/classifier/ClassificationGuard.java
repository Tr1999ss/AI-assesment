package com.shk.generic.ingestion.category.classifier;

import com.shk.generic.ingestion.category.CategoryProperties;
import com.shk.generic.ingestion.category.CategoryResolution;
import com.shk.generic.ingestion.category.CategorySet;
import com.shk.generic.ingestion.category.CategorySource;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Turns untrusted model output into a valid {@link CategoryResolution}:
 * <ul>
 *   <li>names not in the active registry are dropped (models invent categories)</li>
 *   <li>a weak or invalid primary is replaced by the strongest confident overlap, else the fallback</li>
 *   <li>overlaps below the confidence floor are dropped, the rest capped and ordered strongest first</li>
 * </ul>
 */
@Component
public class ClassificationGuard {

    private final CategoryProperties properties;

    public ClassificationGuard(CategoryProperties properties) {
        this.properties = properties;
    }

    public CategoryResolution interpret(@Nullable Classification raw, Set<String> activeNames) {
        String fallback = properties.fallback();
        if (!activeNames.contains(fallback)) {
            throw new IllegalStateException("Fallback category '" + fallback + "' is missing or inactive");
        }
        Classification classification = raw == null ? new Classification(null, null, null) : raw;

        List<Scored> overlaps = classification.overlaps() == null ? List.of() : classification.overlaps().stream()
                .filter(Objects::nonNull)
                .map(o -> new Scored(normalize(o.name()), confidence(o.confidence())))
                .filter(s -> activeNames.contains(s.name()))
                .sorted(Comparator.comparingDouble(Scored::confidence).reversed())
                .toList();

        String primary = normalize(classification.primary());
        boolean primaryTrusted = activeNames.contains(primary)
                && confidence(classification.primaryConfidence()) >= properties.primaryMinConfidence();
        CategorySource source = CategorySource.AI;
        if (!primaryTrusted) {
            primary = overlaps.stream()
                    .filter(s -> s.confidence() >= properties.primaryMinConfidence())
                    .map(Scored::name)
                    .findFirst()
                    .orElse(fallback);
            source = primary.equals(fallback) ? CategorySource.FALLBACK : CategorySource.AI;
        }

        String chosenPrimary = primary;
        List<String> keptOverlaps = overlaps.stream()
                .filter(s -> s.confidence() >= properties.overlapMinConfidence())
                .map(Scored::name)
                .filter(name -> !name.equals(chosenPrimary) && !name.equals(fallback))
                .distinct()
                .limit(properties.maxOverlaps())
                .toList();

        return new CategoryResolution(CategorySet.of(chosenPrimary, keptOverlaps), source);
    }

    private static String normalize(@Nullable String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    private static double confidence(@Nullable Double value) {
        return value == null || value.isNaN() ? 0.0 : Math.max(0.0, Math.min(1.0, value));
    }

    private record Scored(String name, double confidence) {
    }
}
