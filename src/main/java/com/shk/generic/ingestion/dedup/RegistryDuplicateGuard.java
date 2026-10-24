package com.shk.generic.ingestion.dedup;

import com.shk.generic.ingestion.extract.ExtractedDocument;
import com.shk.generic.ingestion.intake.IngestionRequest;
import com.shk.generic.ingestion.registry.DocumentRegistry;
import com.shk.generic.ingestion.registry.NewRegistration;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Check-and-claim in one atomic step: claiming the hash up front means concurrent identical
 * uploads can't both pass the check and both get embedded.
 * A duplicate never modifies the existing entry, even if it carries different categories.
 */
@Component
public class RegistryDuplicateGuard implements DuplicateGuard {

    private final ContentHasher hasher;
    private final DocumentRegistry registry;

    public RegistryDuplicateGuard(ContentHasher hasher, DocumentRegistry registry) {
        this.hasher = hasher;
        this.registry = registry;
    }

    @Override
    public DedupOutcome check(ExtractedDocument document) {
        IngestionRequest request = document.request();
        // Categories stored here are the caller's request; the store stage records the final set.
        DocumentRegistry.Claim claim = registry.claim(new NewRegistration(hasher.hash(document),
                request.filename(), request.format(), request.channel(),
                List.copyOf(request.requestedCategories()), request.submittedBy()));

        return claim.created()
                ? new DedupOutcome.Fresh(claim.document())
                : new DedupOutcome.Duplicate(claim.document());
    }

    @Override
    public void release(DedupOutcome.Fresh claim) {
        registry.release(claim.document().id());
    }
}
