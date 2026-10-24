package com.shk.generic.category.admin;

import com.shk.generic.category.CategoryDefinition;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class JdbcCategoryStore implements CategoryStore {

    private static final RowMapper<CategoryDefinition> ROW_MAPPER = (rs, rowNum) -> new CategoryDefinition(
            rs.getString("name"), rs.getString("description"), rs.getBoolean("active"));

    private final JdbcClient jdbc;

    public JdbcCategoryStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<CategoryDefinition> findAll(boolean includeInactive) {
        return jdbc.sql("SELECT name, description, active FROM category"
                        + (includeInactive ? "" : " WHERE active") + " ORDER BY name")
                .query(ROW_MAPPER)
                .list();
    }

    @Override
    public Optional<CategoryDefinition> find(String name) {
        return jdbc.sql("SELECT name, description, active FROM category WHERE name = :name")
                .param("name", name)
                .query(ROW_MAPPER)
                .optional();
    }

    @Override
    public boolean insert(CategoryDefinition category) {
        return jdbc.sql("""
                        INSERT INTO category (name, description, active) VALUES (:name, :description, :active)
                        ON CONFLICT (name) DO NOTHING
                        """)
                .param("name", category.name())
                .param("description", category.description())
                .param("active", category.active())
                .update() == 1;
    }

    @Override
    public Optional<CategoryDefinition> update(String name, @Nullable String description, @Nullable Boolean active) {
        return jdbc.sql("""
                        UPDATE category
                        SET description = COALESCE(:description, description),
                            active      = COALESCE(:active, active),
                            updated_at  = now()
                        WHERE name = :name
                        RETURNING name, description, active
                        """)
                .param("name", name)
                .param("description", description, java.sql.Types.VARCHAR)
                .param("active", active, java.sql.Types.BOOLEAN)
                .query(ROW_MAPPER)
                .optional();
    }

    @Override
    public boolean delete(String name) {
        return jdbc.sql("DELETE FROM category WHERE name = :name").param("name", name).update() == 1;
    }
}
