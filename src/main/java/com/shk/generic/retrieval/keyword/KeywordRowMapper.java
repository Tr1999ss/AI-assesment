package com.shk.generic.retrieval.keyword;

import com.shk.generic.retrieval.SearchHit;
import com.shk.generic.retrieval.pipeline.ScoreType;
import com.shk.generic.retrieval.search.ChunkHitMapper;
import org.springframework.jdbc.core.RowMapper;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

/** Rows of {@code (id, content, metadata::text, score)} into hits. */
final class KeywordRowMapper {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    private KeywordRowMapper() {
    }

    static RowMapper<SearchHit> of(JsonMapper json, ScoreType type) {
        return (rs, rowNum) -> {
            String metadata = rs.getString("metadata");
            Map<String, Object> m = metadata == null ? Map.of() : json.readValue(metadata, MAP);
            return ChunkHitMapper.toHit(rs.getString("id"), rs.getString("content"), m, rs.getDouble("score"), type);
        };
    }
}
