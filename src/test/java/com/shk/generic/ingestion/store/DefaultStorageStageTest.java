package com.shk.generic.ingestion.store;

import com.shk.generic.ingestion.category.CategorizedDocument;
import com.shk.generic.ingestion.category.CategorySet;
import com.shk.generic.ingestion.category.CategorySource;
import com.shk.generic.ingestion.chunk.ChunkedDocument;
import com.shk.generic.ingestion.extract.ExtractedDocument;
import com.shk.generic.ingestion.intake.IngestionRequest;
import com.shk.generic.ingestion.intake.IntakeChannel;
import com.shk.generic.ingestion.intake.NamedByteArrayResource;
import com.shk.generic.ingestion.intake.SourceFormat;
import com.shk.generic.ingestion.registry.DocumentRegistry;
import com.shk.generic.ingestion.registry.NewRegistration;
import com.shk.generic.ingestion.registry.RegisteredDocument;
import com.shk.generic.ingestion.registry.RegistrationStatus;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultStorageStageTest {

    private final UUID id = UUID.randomUUID();
    private final FakeChunkStore chunkStore = new FakeChunkStore();
    private final FakeRegistry registry = new FakeRegistry();
    private final DefaultStorageStage stage = new DefaultStorageStage(chunkStore, registry);

    @Test
    void writesChunksThenMarksIngestedWithOrderedCategories() {
        StoredDocument stored = stage.store(chunked());

        assertThat(chunkStore.written).hasSize(2);
        assertThat(registry.markedIngested).containsExactly("legal|[legal, hr]|2");
        assertThat(stored.registration().status()).isEqualTo(RegistrationStatus.INGESTED);
        assertThat(chunkStore.deleted).isEmpty();
    }

    @Test
    void writeFailureDeletesPartialChunksAndIsRetryable() {
        chunkStore.writeFailure = new IllegalStateException("embedding 429");

        assertThatThrownBy(() -> stage.store(chunked()))
                .isInstanceOf(StorageException.class)
                .hasRootCauseMessage("embedding 429");
        assertThat(chunkStore.deleted).containsExactly(id);
        assertThat(registry.markedIngested).isEmpty();
    }

    @Test
    void claimReleasedWhileStoringRemovesChunksInsteadOfResurrectingIt() {
        registry.pending = false;   // the sweeper released it

        assertThatThrownBy(() -> stage.store(chunked()))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("no longer pending");
        assertThat(chunkStore.deleted).containsExactly(id);
    }

    @Test
    void cleanupFailureIsAttachedToTheOriginalError() {
        chunkStore.writeFailure = new IllegalStateException("db down");
        chunkStore.deleteFailure = new IllegalStateException("still down");

        assertThatThrownBy(() -> stage.store(chunked()))
                .isInstanceOf(StorageException.class)
                .satisfies(e -> assertThat(e.getCause().getSuppressed())
                        .anySatisfy(s -> assertThat(s).hasMessage("still down")));
    }

    private ChunkedDocument chunked() {
        IngestionRequest request = IngestionRequest.of(IntakeChannel.JSON_TEXT, "a.txt", SourceFormat.TEXT,
                new NamedByteArrayResource(new byte[]{1}, "a.txt"), List.of(), Map.of());
        RegisteredDocument registration = registration(RegistrationStatus.PENDING);
        CategorizedDocument categorized = new CategorizedDocument(
                new ExtractedDocument(request, List.of(new Document("x")), "x"),
                registration, CategorySet.of("legal", List.of("hr")), CategorySource.CALLER);
        return new ChunkedDocument(categorized, List.of(new Document("one"), new Document("two")));
    }

    private RegisteredDocument registration(RegistrationStatus status) {
        return new RegisteredDocument(id, "a".repeat(64), "a.txt", SourceFormat.TEXT, IntakeChannel.JSON_TEXT,
                List.of(), null, null, null, status, Instant.now(), Instant.now());
    }

    private static final class FakeChunkStore implements ChunkStore {
        final List<Document> written = new ArrayList<>();
        final List<UUID> deleted = new ArrayList<>();
        RuntimeException writeFailure;
        RuntimeException deleteFailure;

        @Override
        public void write(List<Document> chunks) {
            if (writeFailure != null) {
                throw writeFailure;
            }
            written.addAll(chunks);
        }

        @Override
        public void deleteByDocumentId(UUID documentId) {
            deleted.add(documentId);
            if (deleteFailure != null) {
                throw deleteFailure;
            }
        }
    }

    private final class FakeRegistry implements DocumentRegistry {
        final List<String> markedIngested = new ArrayList<>();
        boolean pending = true;

        @Override
        public boolean markIngested(UUID docId, String primary, List<String> categories, int chunkCount) {
            if (!pending) {
                return false;
            }
            markedIngested.add(primary + "|" + categories + "|" + chunkCount);
            return true;
        }

        @Override
        public Optional<RegisteredDocument> findById(UUID docId) {
            return Optional.of(registration(RegistrationStatus.INGESTED));
        }

        @Override
        public Claim claim(NewRegistration registration) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<RegisteredDocument> findByHash(String contentHash) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean release(UUID docId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<RegisteredDocument> findStalePending(Duration olderThan, int limit) {
            throw new UnsupportedOperationException();
        }
    }
}
