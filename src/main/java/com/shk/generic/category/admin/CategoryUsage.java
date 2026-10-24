package com.shk.generic.category.admin;

/**
 * How many stored documents use a category. Implemented by whichever module stores documents,
 * so category administration doesn't depend on ingestion internals.
 */
public interface CategoryUsage {

    long documentCount(String category);
}
