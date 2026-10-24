package com.shk.generic.category;

import java.util.List;

/** Caller supplied categories that don't exist or are inactive. */
public class InvalidCategoryException extends RuntimeException {

    public InvalidCategoryException(List<String> invalid, List<String> valid) {
        super("Unknown or inactive categories " + invalid + "; valid categories are " + valid);
    }
}
