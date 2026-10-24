package com.shk.generic.retrieval.keyword;

import com.shk.generic.retrieval.RetrievalUnavailableException;
import com.shk.generic.retrieval.SearchHit;
import com.shk.generic.retrieval.pipeline.ScoreType;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/**
 * True BM25 via ParadeDB pg_search ({@code vector_store_bm25_idx}, from db/migration-paradedb).
 * Uses {@code paradedb.match} with a bound parameter: user text is tokenized, never parsed as query syntax
 * (raw {@code @@@ 'text'} fails on input like {@code "unbalanced} - verified against pg_search 0.21).
 */
@Component
public class ParadeDbBm25Engine implements KeywordEngine {

    static final String INDEX = "vector_store_bm25_idx";

    private static final String SQL = """
            SELECT * FROM (
                SELECT id::text AS id, content, metadata::text AS metadata, paradedb.score(id) AS score
                FROM vector_store
                WHERE id @@@ paradedb.match('content', :query)%s
                ORDER BY score DESC, id
                LIMIT :limit
            ) ranked
            WHERE score >= :minScore
            """;

    private final JdbcClient jdbc;
    private final JsonMapper json;

    public ParadeDbBm25Engine(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public KeywordProperties.KeywordEngineType type() {
        return KeywordProperties.KeywordEngineType.PARADEDB_BM25;
    }

    @Override
    public ScoreType scoreType() {
        return ScoreType.BM25;
    }

    @Override
    public void verify() {
        boolean extension = jdbc.sql("SELECT count(*) > 0 FROM pg_extension WHERE extname = 'pg_search'")
                .query(Boolean.class).single();
        boolean index = jdbc.sql("SELECT count(*) > 0 FROM pg_indexes WHERE tablename = 'vector_store' AND indexname = :i")
                .param("i", INDEX).query(Boolean.class).single();
        if (!extension || !index) {
            throw new IllegalStateException("retrieval.keyword.engine=paradedb-bm25 needs the pg_search extension and "
                    + INDEX + " (found extension=" + extension + ", index=" + index + "). Run against the ParadeDB "
                    + "image (compose.paradedb.yaml) with the 'paradedb' profile, which adds classpath:db/migration-paradedb "
                    + "to spring.flyway.locations.");
        }
    }

    @Override
    public List<SearchHit> search(String text, int limit, double minScore, Filter.@Nullable Expression filter) {
        try {
            return jdbc.sql(SQL.formatted(MetadataFilterSql.andClause(filter)))
                    .param("query", text)
                    .param("limit", limit)
                    .param("minScore", minScore)
                    .query(KeywordRowMapper.of(json, ScoreType.BM25))
                    .list();
        } catch (RuntimeException e) {
            throw new RetrievalUnavailableException("BM25 search failed: " + e.getMessage(), e);
        }
    }
}
