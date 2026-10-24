package com.shk.generic.ingestion.extract.reader;

import com.shk.generic.ingestion.extract.DocumentExtractor;
import com.shk.generic.ingestion.intake.SourceFormat;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

/** One Document per page, with {@code page_number} metadata. */
@Component
public class PdfExtractor implements DocumentExtractor {

    @Override
    public boolean supports(SourceFormat format) {
        return format == SourceFormat.PDF;
    }

    @Override
    public List<Document> extract(Resource content, String filename) {
        return new PagePdfDocumentReader(content, PdfDocumentReaderConfig.defaultConfig()).get();
    }
}
