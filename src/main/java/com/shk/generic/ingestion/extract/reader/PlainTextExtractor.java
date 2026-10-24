package com.shk.generic.ingestion.extract.reader;

import com.shk.generic.ingestion.extract.DocumentExtractor;
import com.shk.generic.ingestion.intake.SourceFormat;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

/** Whole file as one Document; splitting is the chunker's job. */
@Component
public class PlainTextExtractor implements DocumentExtractor {

    @Override
    public boolean supports(SourceFormat format) {
        return format == SourceFormat.TEXT;
    }

    @Override
    public List<Document> extract(Resource content, String filename) {
        return new TextReader(content).get();
    }
}
