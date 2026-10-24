package com.shk.generic.retrieval.diversify;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.List;

/** Cosine similarity between stored chunk embeddings, computed by pgvector in one query (pool ≤ max-candidates). */
@Component
public class JdbcChunkSimilarity implements ChunkSimilarity {

    private final JdbcClient jdbc;

    public JdbcChunkSimilarity(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public SimilarityMatrix pairwise(List<String> chunkIds) {
        SimilarityMatrix matrix = new SimilarityMatrix();
        if (chunkIds.size() < 2) {
            return matrix;
        }
        String[] ids = chunkIds.toArray(String[]::new);
        jdbc.sql("""
                        SELECT a.id::text AS a, b.id::text AS b, 1 - (a.embedding <=> b.embedding) AS sim
                        FROM vector_store a
                        JOIN vector_store b ON a.id < b.id
                        WHERE a.id = ANY(CAST(:ids AS uuid[])) AND b.id = ANY(CAST(:ids AS uuid[]))
                        """)
                .param("ids", ids)
                .query(rs -> {
                    matrix.put(rs.getString("a"), rs.getString("b"), rs.getDouble("sim"));
                });
        return matrix;
    }
}
