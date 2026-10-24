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
import java.util.regex.Pattern;

/**
 * Built-in Postgres full-text search over {@code vector_store.content_tsv} (GIN index, created by V5).
 * <ul>
 *   <li>Plain-language input matches ANY of its terms (OR), like BM25's default, and ranking rewards chunks
 *       matching more terms close together. An AND of every term (websearch_to_tsquery's default) would make
 *       natural questions match nothing: "How many days off do I get for holidays each year?" needs a chunk
 *       containing mani, day, get, holiday and year</li>
 *   <li>Input using search syntax (quotes, -exclusion, or) is parsed by {@code websearch_to_tsquery} with its
 *       exact semantics. Both parsers accept any text without errors</li>
 *   <li>{@code ts_rank_cd(..., 32)} = cover density rank normalized to 0..1 via rank / (rank + 1);
 *       section titles are weighted above body text</li>
 * </ul>
 * Not BM25 (no IDF saturation tuning), but close for chunk-sized documents. See {@link ParadeDbBm25Engine}.
 */
@Component
public class PostgresFullTextEngine implements KeywordEngine {

    /** Must match the configuration used by the content_tsv column in V5. */
    public static final String TEXT_SEARCH_CONFIG = "english";

    private static final String SQL = """
            SELECT id::text AS id, content, metadata::text AS metadata, ts_rank_cd(content_tsv, tq.q, 32) AS score
            FROM vector_store, (SELECT %s AS q) AS tq
            WHERE content_tsv @@ tq.q
              AND ts_rank_cd(content_tsv, tq.q, 32) >= :minScore%s
            ORDER BY score DESC, id
            LIMIT :limit
            """;

    /** Any term (OR): plainto_tsquery normalizes the text, then its ANDs become ORs. */
    static final String ANY_TERM = "replace(plainto_tsquery('" + TEXT_SEARCH_CONFIG + "', :query)::text, ' & ', ' | ')::tsquery";
    static final String WEBSEARCH = "websearch_to_tsquery('" + TEXT_SEARCH_CONFIG + "', :query)";

    /** Quotes, a leading '-' on a word, or a standalone 'or' signal deliberate search syntax. */
    private static final Pattern SEARCH_SYNTAX = Pattern.compile("\"|(^|\\s)-\\S|\\sor\\s", Pattern.CASE_INSENSITIVE);

    private final JdbcClient jdbc;
    private final JsonMapper json;

    public PostgresFullTextEngine(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public KeywordProperties.KeywordEngineType type() {
        return KeywordProperties.KeywordEngineType.POSTGRES_FTS;
    }

    @Override
    public ScoreType scoreType() {
        return ScoreType.FTS_RANK;
    }

    @Override
    public List<SearchHit> search(String text, int limit, double minScore, Filter.@Nullable Expression filter) {
        try {
            return jdbc.sql(SQL.formatted(tsquery(text), MetadataFilterSql.andClause(filter)))
                    .param("query", text)
                    .param("minScore", minScore)
                    .param("limit", limit)
                    .query(KeywordRowMapper.of(json, ScoreType.FTS_RANK))
                    .list();
        } catch (RuntimeException e) {
            throw new RetrievalUnavailableException("Keyword search failed: " + e.getMessage(), e);
        }
    }

    static String tsquery(String text) {
        return SEARCH_SYNTAX.matcher(text).find() ? WEBSEARCH : ANY_TERM;
    }
}
