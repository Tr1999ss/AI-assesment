package com.shk.generic.ingestion.category;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * One primary category plus ordered overlaps (strongest first). Never contains the primary twice.
 */
public record CategorySet(String primary, Set<String> overlaps) {

    public CategorySet {
        Objects.requireNonNull(primary, "primary");
        LinkedHashSet<String> cleaned = new LinkedHashSet<>(overlaps);
        cleaned.remove(primary);
        overlaps = Collections.unmodifiableSet(cleaned);
    }

    public static CategorySet of(String primary, Collection<String> overlaps) {
        return new CategorySet(primary, new LinkedHashSet<>(overlaps));
    }

    /** Primary first, then overlaps: what chunks get tagged with. */
    public List<String> all() {
        List<String> all = new ArrayList<>(overlaps.size() + 1);
        all.add(primary);
        all.addAll(overlaps);
        return List.copyOf(all);
    }
}
