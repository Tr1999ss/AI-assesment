package com.shk.generic.ingestion.extract;

import com.shk.generic.ingestion.intake.SourceFormat;
import org.springframework.core.io.Resource;
import org.springframework.ai.document.Document;

import java.util.List;

/** Reads one format into Spring AI Documents. One implementation per format. */
public interface DocumentExtractor {

    boolean supports(SourceFormat format);

    List<Document> extract(Resource content, String filename);
}
