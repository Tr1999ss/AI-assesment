package com.shk.generic.category.admin;

/** The change conflicts with existing state: duplicate name, protected category, or category in use. */
public class CategoryConflictException extends RuntimeException {

    public CategoryConflictException(String message) {
        super(message);
    }
}
