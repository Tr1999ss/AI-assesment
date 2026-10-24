package com.shk.generic.ingestion.intake;

import com.shk.generic.ingestion.IngestionResult;
import com.shk.generic.ingestion.IngestionService;
import com.shk.generic.security.CurrentUser;
import org.springframework.stereotype.Component;

/**
 * The one path every transport edge uses: adapt → stamp submitter → validate → ingest.
 * Edges stay thin and can't forget validation or auditing.
 */
@Component
public class IntakeGateway {

    private final IntakeValidator validator;
    private final IngestionService ingestionService;
    private final CurrentUser currentUser;

    public IntakeGateway(IntakeValidator validator, IngestionService ingestionService, CurrentUser currentUser) {
        this.validator = validator;
        this.ingestionService = ingestionService;
        this.currentUser = currentUser;
    }

    public <S> IngestionResult submit(IntakeAdapter<S> adapter, S source) {
        IngestionRequest request = adapter.adapt(source).withSubmittedBy(currentUser.username().orElse(null));
        validator.validate(request);
        return ingestionService.ingest(request);
    }
}
