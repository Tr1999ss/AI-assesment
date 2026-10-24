package com.shk.generic.ingestion.intake;

import com.shk.generic.ingestion.IngestionResult;
import com.shk.generic.ingestion.IngestionService;
import org.springframework.stereotype.Component;

/**
 * The one path every transport edge uses: adapt → validate → ingest.
 * Edges stay thin and can't forget validation.
 */
@Component
public class IntakeGateway {

    private final IntakeValidator validator;
    private final IngestionService ingestionService;

    public IntakeGateway(IntakeValidator validator, IngestionService ingestionService) {
        this.validator = validator;
        this.ingestionService = ingestionService;
    }

    public <S> IngestionResult submit(IntakeAdapter<S> adapter, S source) {
        IngestionRequest request = adapter.adapt(source);
        validator.validate(request);
        return ingestionService.ingest(request);
    }
}
