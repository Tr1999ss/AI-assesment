package com.shk.generic.category.admin;

import java.util.Optional;

/**
 * Lets another module veto deactivating or deleting a category it depends on
 * (e.g. ingestion's fallback category). Every bean is consulted.
 */
public interface ProtectedCategoryPolicy {

    /** @return why the category can't be deactivated or deleted, or empty if this policy has no objection */
    Optional<String> protectionReason(String category);
}
