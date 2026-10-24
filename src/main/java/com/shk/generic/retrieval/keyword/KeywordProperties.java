package com.shk.generic.retrieval.keyword;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param engine   POSTGRES_FTS (built in) or PARADEDB_BM25 (needs the ParadeDB image and the paradedb profile)
 * @param minScore relevance floor in the engine's own scale (FTS: ts_rank_cd normalized to 0..1; BM25: raw score).
 *                 0 = any lexical match counts
 */
@ConfigurationProperties("retrieval.keyword")
public record KeywordProperties(
        @DefaultValue("postgres-fts") KeywordEngineType engine,
        @DefaultValue("0.0") double minScore
) {
    public enum KeywordEngineType {
        POSTGRES_FTS,
        PARADEDB_BM25
    }
}
