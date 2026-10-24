package com.shk.generic.category.admin;

public class CategoryNotFoundException extends RuntimeException {

    public CategoryNotFoundException(String name) {
        super("Category '" + name + "' does not exist");
    }
}
