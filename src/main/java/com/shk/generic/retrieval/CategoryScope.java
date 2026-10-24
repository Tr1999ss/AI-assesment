package com.shk.generic.retrieval;

/** How a category filter is applied. */
public enum CategoryScope {
    /** Chunk is tagged with any of the requested categories (primary or overlap). */
    ANY,
    /** Chunk's primary category is one of the requested categories. */
    PRIMARY
}
