package com.shk.generic.ingestion.registry;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Record of every unique document. Shared port: the duplicate check claims hashes,
 * the store stage finalizes them, the sweeper cleans up abandoned claims.
 */
public interface DocumentRegistry {

    /**
     * Atomically registers the hash, or returns the document that already owns it.
     * Safe under concurrency: exactly one caller per hash gets {@code created == true}.
     */
    Claim claim(NewRegistration registration);

    Optional<RegisteredDocument> findByHash(String contentHash);

    Optional<RegisteredDocument> findById(UUID id);

    /**
     * PENDING → INGESTED, recording the final categories (primary first) and chunk count.
     *
     * @return false if the entry is no longer PENDING (e.g. released by the sweeper meanwhile)
     */
    boolean markIngested(UUID id, String primaryCategory, List<String> categories, int chunkCount);

    /**
     * Undoes a claim whose ingestion failed, so the same content can be uploaded again.
     * Only removes {@link RegistrationStatus#PENDING} entries; ingested documents are never touched.
     *
     * @return true if an entry was removed
     */
    boolean release(UUID id);

    /** PENDING entries created more than {@code olderThan} ago, oldest first. */
    List<RegisteredDocument> findStalePending(Duration olderThan, int limit);

    /** @param created false when the hash was already registered and {@code document} is the existing one */
    record Claim(RegisteredDocument document, boolean created) {
    }
}
