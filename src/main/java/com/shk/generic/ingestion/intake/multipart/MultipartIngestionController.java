package com.shk.generic.ingestion.intake.multipart;

import com.shk.generic.ingestion.IngestionResult;
import com.shk.generic.ingestion.intake.IntakeGateway;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

@RestController
@RequestMapping("/api/ingest")
public class MultipartIngestionController {

    private final IntakeGateway gateway;
    private final MultipartIntakeAdapter adapter;

    public MultipartIngestionController(IntakeGateway gateway, MultipartIntakeAdapter adapter) {
        this.gateway = gateway;
        this.adapter = adapter;
    }

    @PostMapping(path = "/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public IngestionResult ingestFile(@RequestPart("file") MultipartFile file,
                                      @RequestParam(name = "categories", required = false) Set<String> categories) {
        return gateway.submit(adapter, new MultipartUpload(file, categories == null ? Set.of() : categories));
    }
}
