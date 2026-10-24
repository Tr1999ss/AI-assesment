package com.shk.generic.ingestion.store;

import com.shk.generic.ingestion.category.CategorySet;
import com.shk.generic.ingestion.chunk.ChunkedDocument;
import com.shk.generic.ingestion.registry.DocumentRegistry;
import com.shk.generic.ingestion.registry.RegisteredDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Writes chunks (embedding them), then flips the registry entry to INGESTED.
 * <p>
 * Not one DB transaction: embedding is HTTP calls, and a transaction held across them would pin
 * a connection. Instead this stage compensates its own side effect: if anything fails after chunks
 * may have been written, they are deleted by {@code document_id}. Releasing the claim is the
 * pipeline's job. Chunk ids are deterministic, so a partial write followed by a retry can't duplicate.
 */
@Slf4j
@Component
public class DefaultStorageStage implements StorageStage {

    private final ChunkStore chunkStore;
    private final DocumentRegistry registry;

    public DefaultStorageStage(ChunkStore chunkStore, DocumentRegistry registry) {
        this.chunkStore = chunkStore;
        this.registry = registry;
    }

    @Override
    public StoredDocument store(ChunkedDocument document) {
        UUID id = document.categorized().registration().id();
        CategorySet categories = document.categorized().categories();
        try {
            chunkStore.write(document.chunks());
            // Guarded by status = PENDING: if the sweeper released this claim meanwhile, we must not resurrect it.
            if (!registry.markIngested(id, categories.primary(), categories.all(), document.chunks().size())) {
                throw new StorageException("Document " + id + " is no longer pending; it was released while storing");
            }
        } catch (RuntimeException e) {
            deleteChunksQuietly(id, e);
            throw e instanceof StorageException ? e : new StorageException("Storing chunks failed: " + e.getMessage(), e);
        }
        RegisteredDocument ingested = registry.findById(id).orElseThrow();
        return new StoredDocument(document, ingested);
    }

    private void deleteChunksQuietly(UUID id, RuntimeException cause) {
        try {
            chunkStore.deleteByDocumentId(id);
        } catch (RuntimeException deleteFailure) {
            cause.addSuppressed(deleteFailure);
            log.error("Could not remove chunks of document {} after failed store", id, deleteFailure);
        }
    }
}
