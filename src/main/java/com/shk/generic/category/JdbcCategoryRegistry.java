package com.shk.generic.category;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reads the {@code category} table and caches a snapshot for {@code category.registry.cache-ttl},
 * so classification and search don't hit the database per request. A {@link CategoryChangedEvent}
 * drops the snapshot immediately.
 */
@Repository
public class JdbcCategoryRegistry implements CategoryRegistry {

    static final RowMapper<CategoryDefinition> ROW_MAPPER = (rs, rowNum) -> new CategoryDefinition(
            rs.getString("name"), rs.getString("description"), rs.getBoolean("active"));

    private final JdbcClient jdbc;
    private final CategoryRegistryProperties properties;
    private final Clock clock;

    private volatile Snapshot snapshot;

    @Autowired
    public JdbcCategoryRegistry(JdbcClient jdbc, CategoryRegistryProperties properties) {
        this(jdbc, properties, Clock.systemUTC());
    }

    JdbcCategoryRegistry(JdbcClient jdbc, CategoryRegistryProperties properties, Clock clock) {
        this.jdbc = jdbc;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public List<CategoryDefinition> active() {
        return snapshot().all().values().stream().filter(CategoryDefinition::active).toList();
    }

    @Override
    public Optional<CategoryDefinition> find(String name) {
        return Optional.ofNullable(snapshot().all().get(name));
    }

    @EventListener
    public void onCategoryChanged(CategoryChangedEvent event) {
        snapshot = null;
    }

    private Snapshot snapshot() {
        Snapshot current = snapshot;
        Instant now = clock.instant();
        if (current == null || current.loadedAt().plus(properties.cacheTtl()).isBefore(now)) {
            current = new Snapshot(load(), now);
            snapshot = current;
        }
        return current;
    }

    private Map<String, CategoryDefinition> load() {
        Map<String, CategoryDefinition> byName = new LinkedHashMap<>();
        jdbc.sql("SELECT name, description, active FROM category ORDER BY name")
                .query(ROW_MAPPER)
                .list()
                .forEach(c -> byName.put(c.name(), c));
        return Collections.unmodifiableMap(byName);
    }

    private record Snapshot(Map<String, CategoryDefinition> all, Instant loadedAt) {
    }
}
