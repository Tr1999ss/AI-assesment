package com.shk.generic.category;

import java.util.regex.Pattern;

/** Category name rules, mirrored by the {@code ck_category_name} database constraint. */
public final class CategoryNames {

    public static final String PATTERN = "^[a-z0-9][a-z0-9_-]*$";
    public static final int MAX_LENGTH = 64;

    private static final Pattern COMPILED = Pattern.compile(PATTERN);

    private CategoryNames() {
    }

    public static boolean isValid(String name) {
        return name != null && name.length() <= MAX_LENGTH && COMPILED.matcher(name).matches();
    }
}
