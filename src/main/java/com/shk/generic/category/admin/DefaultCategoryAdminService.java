package com.shk.generic.category.admin;

import com.shk.generic.category.CategoryChangedEvent;
import com.shk.generic.category.CategoryChangedEvent.Change;
import com.shk.generic.category.CategoryDefinition;
import com.shk.generic.category.CategoryNames;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Slf4j
@Service
public class DefaultCategoryAdminService implements CategoryAdminService {

    private final CategoryStore store;
    private final CategoryUsage usage;
    private final List<ProtectedCategoryPolicy> protectionPolicies;
    private final ApplicationEventPublisher events;

    public DefaultCategoryAdminService(CategoryStore store, CategoryUsage usage,
                                       List<ProtectedCategoryPolicy> protectionPolicies,
                                       ApplicationEventPublisher events) {
        this.store = store;
        this.usage = usage;
        this.protectionPolicies = protectionPolicies;
        this.events = events;
    }

    @Override
    public List<CategoryDefinition> list(boolean includeInactive) {
        return store.findAll(includeInactive);
    }

    @Override
    public CategoryDefinition get(String name) {
        return store.find(normalize(name)).orElseThrow(() -> new CategoryNotFoundException(name));
    }

    @Override
    public CategoryDefinition create(String name, String description) {
        String normalized = normalize(name);
        if (!CategoryNames.isValid(normalized)) {
            throw new IllegalArgumentException("Invalid category name '" + name + "'");
        }
        CategoryDefinition category = new CategoryDefinition(normalized, description.strip(), true);
        if (!store.insert(category)) {
            throw new CategoryConflictException("Category '" + normalized + "' already exists");
        }
        publish(normalized, Change.CREATED);
        return category;
    }

    @Override
    public CategoryDefinition update(String name, @Nullable String description, @Nullable Boolean active) {
        String normalized = normalize(name);
        if (Boolean.FALSE.equals(active)) {
            requireUnprotected(normalized, "deactivated");
        }
        CategoryDefinition updated = store.update(normalized, description == null ? null : description.strip(), active)
                .orElseThrow(() -> new CategoryNotFoundException(normalized));
        publish(normalized, Change.UPDATED);
        return updated;
    }

    @Override
    public void delete(String name) {
        String normalized = normalize(name);
        store.find(normalized).orElseThrow(() -> new CategoryNotFoundException(normalized));
        requireUnprotected(normalized, "deleted");
        long documents = usage.documentCount(normalized);
        if (documents > 0) {
            throw new CategoryConflictException("Category '" + normalized + "' is used by " + documents
                    + " document(s); deactivate it instead");
        }
        if (store.delete(normalized)) {
            publish(normalized, Change.DELETED);
        }
    }

    private void requireUnprotected(String name, String action) {
        for (ProtectedCategoryPolicy policy : protectionPolicies) {
            policy.protectionReason(name).ifPresent(reason -> {
                throw new CategoryConflictException("Category '" + name + "' cannot be " + action + ": " + reason);
            });
        }
    }

    private void publish(String name, Change change) {
        log.info("Category '{}' {}", name, change.name().toLowerCase(Locale.ROOT));
        events.publishEvent(new CategoryChangedEvent(name, change));
    }

    private static String normalize(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }
}
