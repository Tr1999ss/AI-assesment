package com.shk.generic.ingestion.extract;

import com.shk.generic.ingestion.intake.IngestionRequest;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Composes resolver + extractor + normalizer, drops empty sections and stamps
 * the metadata every section carries regardless of format.
 */
@Component
public class DefaultExtractionStage implements ExtractionStage {

    public static final String SOURCE = "source";
    public static final String FORMAT = "format";
    public static final String SECTION_INDEX = "section_index";

    private final ExtractorResolver resolver;
    private final TextNormalizer normalizer;

    public DefaultExtractionStage(ExtractorResolver resolver, TextNormalizer normalizer) {
        this.resolver = resolver;
        this.normalizer = normalizer;
    }

    @Override
    public ExtractedDocument extract(IngestionRequest request) {
        List<Document> raw = read(request);

        List<Document> sections = new ArrayList<>();
        for (Document doc : raw) {
            if (doc.getText() == null || doc.getText().isBlank()) {
                continue;
            }
            Map<String, Object> metadata = new HashMap<>(doc.getMetadata());
            metadata.put(SOURCE, request.filename());
            metadata.put(FORMAT, request.format().name());
            metadata.put(SECTION_INDEX, sections.size());
            sections.add(doc.mutate().metadata(metadata).build());
        }
        if (sections.isEmpty()) {
            throw new ExtractionException("No extractable text in '" + request.filename()
                    + "' (scanned/image-only PDFs are not supported)");
        }
        return new ExtractedDocument(request, sections, normalizer.normalize(sections));
    }

    private List<Document> read(IngestionRequest request) {
        try {
            return resolver.resolve(request.format()).extract(request.content(), request.filename());
        } catch (ExtractionException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ExtractionException("Could not read '" + request.filename() + "' as " + request.format(), e);
        }
    }
}
