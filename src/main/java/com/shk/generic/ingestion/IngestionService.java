package com.shk.generic.ingestion;

import com.shk.generic.ingestion.intake.IngestionRequest;

/**
 * Inbound port of the ingestion pipeline. Every transport (REST, MCP, ...) ends here.
 */
public interface IngestionService {

    IngestionResult ingest(IngestionRequest request);
}
