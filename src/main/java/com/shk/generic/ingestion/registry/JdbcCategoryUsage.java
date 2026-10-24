package com.shk.generic.ingestion.registry;

import com.shk.generic.category.admin.CategoryUsage;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Counts registry entries (pending or ingested) tagged with a category. */
@Repository
public class JdbcCategoryUsage implements CategoryUsage {

    private final JdbcClient jdbc;

    public JdbcCategoryUsage(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public long documentCount(String category) {
        return jdbc.sql("SELECT count(*) FROM document_registry WHERE :category = ANY(categories)")
                .param("category", category)
                .query(Long.class)
                .single();
    }
}
