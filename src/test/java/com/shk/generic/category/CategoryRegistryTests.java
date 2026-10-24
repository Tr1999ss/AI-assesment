package com.shk.generic.category;

import com.shk.generic.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CategoryRegistryTests extends AbstractIntegrationTest {

    @Autowired
    JdbcClient jdbc;

    @Autowired
    CategoryRegistryProperties properties;

    private final String name = "temp-" + UUID.randomUUID().toString().substring(0, 8);

    @AfterEach
    void cleanUp() {
        jdbc.sql("DELETE FROM category WHERE name = :n").param("n", name).update();
    }

    @Test
    void tableEditsShowUpAfterTheCacheTtl() {
        MutableClock clock = new MutableClock();
        JdbcCategoryRegistry registry = new JdbcCategoryRegistry(jdbc, properties, clock);

        assertThat(registry.find(name)).isEmpty();
        insert();
        assertThat(registry.find(name)).as("still cached").isEmpty();

        clock.advance(properties.cacheTtl().plusSeconds(1));
        assertThat(registry.find(name)).as("reloaded").isPresent();
    }

    @Test
    void changeEventDropsTheCacheImmediately() {
        JdbcCategoryRegistry registry = new JdbcCategoryRegistry(jdbc, properties, new MutableClock());
        assertThat(registry.find(name)).isEmpty();
        insert();

        registry.onCategoryChanged(new CategoryChangedEvent(name, CategoryChangedEvent.Change.CREATED));

        assertThat(registry.find(name)).isPresent();
    }

    private void insert() {
        jdbc.sql("INSERT INTO category (name, description) VALUES (:n, 'temporary')").param("n", name).update();
    }

    private static final class MutableClock extends Clock {

        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
