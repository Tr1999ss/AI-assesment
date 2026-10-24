package com.shk.generic.security;

/**
 * Keycloak realm roles. Each includes the ones above it (Keycloak composites, mirrored by the
 * {@code RoleHierarchy} bean so a token carrying only {@code kb-admin} still works).
 */
public final class Roles {

    /** Search, ask, list categories. */
    public static final String READER = "kb-reader";
    /** Reader plus ingesting documents. */
    public static final String EDITOR = "kb-editor";
    /** Editor plus category administration. */
    public static final String ADMIN = "kb-admin";

    /** For {@code @PreAuthorize}: SpEL needs compile-time constants. */
    public static final String HAS_READER = "hasRole('" + READER + "')";
    public static final String HAS_EDITOR = "hasRole('" + EDITOR + "')";

    private Roles() {
    }
}
