package com.shk.generic.ingestion.intake.text;

import com.shk.generic.ingestion.IngestionResult;
import com.shk.generic.ingestion.intake.IntakeGateway;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ingest")
public class TextIngestionController {

    private final IntakeGateway gateway;
    private final JsonTextIntakeAdapter adapter;

    public TextIngestionController(IntakeGateway gateway, JsonTextIntakeAdapter adapter) {
        this.gateway = gateway;
        this.adapter = adapter;
    }

    @PostMapping(path = "/text", consumes = MediaType.APPLICATION_JSON_VALUE)
    public IngestionResult ingestText(@Valid @RequestBody TextIngestPayload payload) {
        return gateway.submit(adapter, payload);
    }
}
