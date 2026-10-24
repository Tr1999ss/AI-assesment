package com.shk.generic.ingestion.store;

import com.shk.generic.ingestion.registry.DocumentRegistry;
import com.shk.generic.ingestion.registry.RegisteredDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Cleans up claims left PENDING by a crashed or killed ingestion, so their content can be uploaded again.
 * <p>
 * Order matters: the claim is released first, and chunks are deleted only if that succeeded.
 * A still-running (slow) ingestion then fails its own {@code markIngested} and deletes its chunks,
 * so a document can never end up INGESTED without chunks.
 */
@Slf4j
@Component
public class StalePendingSweeper {

    private final DocumentRegistry registry;
    private final ChunkStore chunkStore;
    private final StoreProperties properties;

    public StalePendingSweeper(DocumentRegistry registry, ChunkStore chunkStore, StoreProperties properties) {
        this.registry = registry;
        this.chunkStore = chunkStore;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${ingestion.store.sweep-interval:5m}",
            initialDelayString = "${ingestion.store.sweep-interval:5m}")
    public void scheduledSweep() {
        try {
            int swept = sweep();
            if (swept > 0) {
                log.info("Released {} stale pending claim(s)", swept);
            }
        } catch (RuntimeException e) {
            log.error("Stale claim sweep failed; will retry next interval", e);
        }
    }

    /** @return number of claims released */
    public int sweep() {
        int released = 0;
        for (RegisteredDocument stale : registry.findStalePending(properties.staleAfter(), properties.sweepBatchSize())) {
            if (registry.release(stale.id())) {
                chunkStore.deleteByDocumentId(stale.id());
                log.warn("Released stale claim {} ({}, created {})", stale.id(), stale.filename(), stale.createdAt());
                released++;
            }
        }
        return released;
    }
}
