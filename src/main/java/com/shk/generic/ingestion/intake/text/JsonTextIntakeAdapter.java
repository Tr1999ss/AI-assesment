package com.shk.generic.ingestion.intake.text;

import com.shk.generic.ingestion.intake.FormatDetector;
import com.shk.generic.ingestion.intake.IngestionRequest;
import com.shk.generic.ingestion.intake.IntakeAdapter;
import com.shk.generic.ingestion.intake.IntakeChannel;
import com.shk.generic.ingestion.intake.NamedByteArrayResource;
import com.shk.generic.ingestion.intake.SourceFormat;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/** Inline text sent in a JSON request body. */
@Component
public class JsonTextIntakeAdapter implements IntakeAdapter<TextIngestPayload> {

    private final FormatDetector formatDetector;

    public JsonTextIntakeAdapter(FormatDetector formatDetector) {
        this.formatDetector = formatDetector;
    }

    @Override
    public IngestionRequest adapt(TextIngestPayload source) {
        String title = source.title().strip();
        byte[] bytes = source.text().getBytes(StandardCharsets.UTF_8);
        SourceFormat format = formatDetector.detect(source.format(), title, null,
                new NamedByteArrayResource(bytes, title));
        String filename = SourceFormat.fromFilename(title).isPresent()
                ? title
                : title + "." + format.defaultExtension();

        return IngestionRequest.of(IntakeChannel.JSON_TEXT, filename, format,
                new NamedByteArrayResource(bytes, filename), source.categories(), source.attributes());
    }
}
