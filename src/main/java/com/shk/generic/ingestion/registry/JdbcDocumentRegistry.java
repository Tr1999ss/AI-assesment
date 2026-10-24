package com.shk.generic.ingestion.registry;

import com.shk.generic.ingestion.intake.IntakeChannel;
import com.shk.generic.ingestion.intake.SourceFormat;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcDocumentRegistry implements DocumentRegistry {

    /** A conflicting row can vanish between INSERT and SELECT only if its writer rolled back; retry then. */
    private static final int MAX_CLAIM_ATTEMPTS = 3;

    // ON CONFLICT relies on uq_document_registry_content_hash: a unique violation becomes
    // "0 rows inserted" instead of an exception. If the conflicting row is still uncommitted,
    // Postgres waits for that transaction before deciding.
    private static final String INSERT_IF_ABSENT = """
            INSERT INTO document_registry (id, content_hash, filename, format, channel, categories, status, ingested_by)
            VALUES (:id, :contentHash, :filename, :format, :channel, :categories, :status, :ingestedBy)
            ON CONFLICT (content_hash) DO NOTHING
            """;

    private static final String SELECT = """
            SELECT id, content_hash, filename, format, channel, categories, primary_category, chunk_count,
                   ingested_by, status, created_at, updated_at
            FROM document_registry
            """;

    private static final RowMapper<RegisteredDocument> ROW_MAPPER = (rs, rowNum) -> new RegisteredDocument(
            rs.getObject("id", UUID.class),
            rs.getString("content_hash"),
            rs.getString("filename"),
            SourceFormat.valueOf(rs.getString("format")),
            IntakeChannel.valueOf(rs.getString("channel")),
            toList(rs.getArray("categories")),
            rs.getString("primary_category"),
            rs.getObject("chunk_count", Integer.class),
            rs.getString("ingested_by"),
            RegistrationStatus.valueOf(rs.getString("status")),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant());

    private final JdbcClient jdbc;

    public JdbcDocumentRegistry(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Claim claim(NewRegistration registration) {
        for (int attempt = 1; attempt <= MAX_CLAIM_ATTEMPTS; attempt++) {
            UUID id = UUID.randomUUID();
            int inserted = jdbc.sql(INSERT_IF_ABSENT)
                    .param("id", id)
                    .param("contentHash", registration.contentHash())
                    .param("filename", registration.filename())
                    .param("format", registration.format().name())
                    .param("channel", registration.channel().name())
                    .param("categories", registration.categories().toArray(String[]::new))
                    .param("status", RegistrationStatus.PENDING.name())
                    .param("ingestedBy", registration.ingestedBy(), java.sql.Types.VARCHAR)
                    .update();
            if (inserted == 1) {
                return new Claim(findById(id).orElseThrow(), true);
            }
            Optional<RegisteredDocument> existing = findByHash(registration.contentHash());
            if (existing.isPresent()) {
                return new Claim(existing.get(), false);
            }
        }
        throw new IllegalStateException("Could not claim content hash " + registration.contentHash()
                + " after " + MAX_CLAIM_ATTEMPTS + " attempts");
    }

    @Override
    public Optional<RegisteredDocument> findByHash(String contentHash) {
        return jdbc.sql(SELECT + "WHERE content_hash = :hash")
                .param("hash", contentHash)
                .query(ROW_MAPPER)
                .optional();
    }

    @Override
    public Optional<RegisteredDocument> findById(UUID id) {
        return jdbc.sql(SELECT + "WHERE id = :id")
                .param("id", id)
                .query(ROW_MAPPER)
                .optional();
    }

    @Override
    public boolean markIngested(UUID id, String primaryCategory, List<String> categories, int chunkCount) {
        return jdbc.sql("""
                        UPDATE document_registry
                        SET status = :ingested, primary_category = :primary, categories = :categories,
                            chunk_count = :chunkCount, updated_at = now()
                        WHERE id = :id AND status = :pending
                        """)
                .param("ingested", RegistrationStatus.INGESTED.name())
                .param("primary", primaryCategory)
                .param("categories", categories.toArray(String[]::new))
                .param("chunkCount", chunkCount)
                .param("id", id)
                .param("pending", RegistrationStatus.PENDING.name())
                .update() == 1;
    }

    @Override
    public boolean release(UUID id) {
        return jdbc.sql("DELETE FROM document_registry WHERE id = :id AND status = :status")
                .param("id", id)
                .param("status", RegistrationStatus.PENDING.name())
                .update() == 1;
    }

    @Override
    public List<RegisteredDocument> findStalePending(Duration olderThan, int limit) {
        return jdbc.sql(SELECT + """
                        WHERE status = :pending AND created_at < now() - make_interval(secs => :seconds)
                        ORDER BY created_at
                        LIMIT :limit
                        """)
                .param("pending", RegistrationStatus.PENDING.name())
                .param("seconds", (double) olderThan.toSeconds())
                .param("limit", limit)
                .query(ROW_MAPPER)
                .list();
    }

    private static List<String> toList(Array array) throws SQLException {
        return array == null ? List.of() : List.of((String[]) array.getArray());
    }
}
