package com.shk.generic.category;

/** Published after a category is created, updated or deleted. Caches and other listeners react to it. */
public record CategoryChangedEvent(String name, Change change) {

    public enum Change {
        CREATED,
        UPDATED,
        DELETED
    }
}
