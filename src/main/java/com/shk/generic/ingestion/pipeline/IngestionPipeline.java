package com.shk.generic.ingestion.pipeline;

import com.shk.generic.ingestion.IngestionResult;
import com.shk.generic.ingestion.IngestionService;
import com.shk.generic.ingestion.IngestionStatus;
import com.shk.generic.ingestion.category.CategorizationStage;
import com.shk.generic.ingestion.category.CategorizedDocument;
import com.shk.generic.ingestion.chunk.ChunkedDocument;
import com.shk.generic.ingestion.chunk.ChunkingStage;
import com.shk.generic.ingestion.dedup.DedupOutcome;
import com.shk.generic.ingestion.dedup.DuplicateGuard;
import com.shk.generic.ingestion.extract.ExtractedDocument;
import com.shk.generic.ingestion.extract.ExtractionStage;
import com.shk.generic.ingestion.intake.IngestionRequest;
import com.shk.generic.ingestion.registry.RegisteredDocument;
import com.shk.generic.ingestion.store.StorageStage;
import com.shk.generic.ingestion.store.StoredDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * The only class that knows stage order. Each stage is an interface; this class just composes them:
 * extract → dedup (claim) → categorize → chunk → store (embeds while writing).
 */
@Slf4j
@Service
public class IngestionPipeline implements IngestionService {

    private final ExtractionStage extraction;
    private final DuplicateGuard duplicateGuard;
    private final CategorizationStage categorization;
    private final ChunkingStage chunking;
    private final StorageStage storage;

    public IngestionPipeline(ExtractionStage extraction, DuplicateGuard duplicateGuard,
                             CategorizationStage categorization, ChunkingStage chunking, StorageStage storage) {
        this.extraction = extraction;
        this.duplicateGuard = duplicateGuard;
        this.categorization = categorization;
        this.chunking = chunking;
        this.storage = storage;
    }

    @Override
    public IngestionResult ingest(IngestionRequest request) {
        ExtractedDocument extracted = extraction.extract(request);
        log.info("[{}] extracted {} section(s), {} chars from {} ({})", request.requestId(),
                extracted.sections().size(), extracted.normalizedText().length(),
                request.filename(), request.format());

        DedupOutcome outcome = duplicateGuard.check(extracted);
        if (outcome instanceof DedupOutcome.Duplicate duplicate) {
            RegisteredDocument existing = duplicate.document();
            log.info("[{}] duplicate of document {} ({}, {})", request.requestId(), existing.id(),
                    existing.filename(), existing.status());
            return result(request, existing, IngestionStatus.DUPLICATE);
        }
        DedupOutcome.Fresh claim = (DedupOutcome.Fresh) outcome;
        log.info("[{}] registered as document {}", request.requestId(), claim.document().id());

        // Every stage after the claim runs here: on failure the claim is released so the content can be retried.
        try {
            return ingestClaimed(request, extracted, claim.document());
        } catch (RuntimeException e) {
            releaseQuietly(claim, e);
            throw e;
        }
    }

    private IngestionResult ingestClaimed(IngestionRequest request, ExtractedDocument extracted,
                                          RegisteredDocument registered) {
        CategorizedDocument categorized = categorization.categorize(extracted, registered);
        log.info("[{}] categories {} (primary {}, source {})", request.requestId(),
                categorized.categories().all(), categorized.categories().primary(), categorized.source());

        ChunkedDocument chunked = chunking.chunk(categorized);
        log.info("[{}] split into {} chunk(s)", request.requestId(), chunked.chunks().size());

        StoredDocument stored = storage.store(chunked);
        log.info("[{}] stored document {}", request.requestId(), stored.registration().id());

        return result(request, stored.registration(), IngestionStatus.INGESTED);
    }

    /**
     * Always reports the registry entry, so a duplicate shows the existing document, not the caller's input.
     * A duplicate of a document still in progress has no primary category or chunk count yet.
     */
    private static IngestionResult result(IngestionRequest request, RegisteredDocument document,
                                          IngestionStatus status) {
        return new IngestionResult(request.requestId(), document.id().toString(), status,
                document.filename(), document.format(), document.primaryCategory(), document.categories(),
                document.chunkCount() == null ? 0 : document.chunkCount());
    }

    private void releaseQuietly(DedupOutcome.Fresh claim, RuntimeException cause) {
        try {
            duplicateGuard.release(claim);
            log.info("Released claim on document {} after failure: {}", claim.document().id(), cause.getMessage());
        } catch (RuntimeException releaseFailure) {
            cause.addSuppressed(releaseFailure);
            log.error("Could not release claim on document {}; it stays PENDING", claim.document().id(), releaseFailure);
        }
    }
}
