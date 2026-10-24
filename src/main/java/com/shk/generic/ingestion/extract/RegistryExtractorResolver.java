package com.shk.generic.ingestion.extract;

import com.shk.generic.ingestion.intake.IntakeProperties;
import com.shk.generic.ingestion.intake.SourceFormat;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Picks the extractor for a format from every {@link DocumentExtractor} bean.
 * Fails at startup if an allowed format has no extractor, rather than on the first request.
 */
@Component
public class RegistryExtractorResolver implements ExtractorResolver {

    private final Map<SourceFormat, DocumentExtractor> byFormat = new EnumMap<>(SourceFormat.class);

    public RegistryExtractorResolver(List<DocumentExtractor> extractors, IntakeProperties intakeProperties) {
        for (SourceFormat format : SourceFormat.values()) {
            List<DocumentExtractor> matches = extractors.stream().filter(e -> e.supports(format)).toList();
            if (matches.size() > 1) {
                throw new IllegalStateException("Multiple extractors support " + format + ": " + matches);
            }
            if (!matches.isEmpty()) {
                byFormat.put(format, matches.get(0));
            }
        }
        intakeProperties.allowedFormats().stream()
                .filter(f -> !byFormat.containsKey(f))
                .findFirst()
                .ifPresent(f -> {
                    throw new IllegalStateException("Format " + f + " is allowed but has no DocumentExtractor");
                });
    }

    @Override
    public DocumentExtractor resolve(SourceFormat format) {
        DocumentExtractor extractor = byFormat.get(format);
        if (extractor == null) {
            throw new ExtractionException("No extractor for format " + format);
        }
        return extractor;
    }
}
