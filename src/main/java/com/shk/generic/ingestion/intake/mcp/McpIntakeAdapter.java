package com.shk.generic.ingestion.intake.mcp;

import com.shk.generic.ingestion.intake.FormatDetector;
import com.shk.generic.ingestion.intake.IngestionRequest;
import com.shk.generic.ingestion.intake.IntakeAdapter;
import com.shk.generic.ingestion.intake.IntakeChannel;
import com.shk.generic.ingestion.intake.IntakeRejectedException;
import com.shk.generic.ingestion.intake.NamedByteArrayResource;
import com.shk.generic.ingestion.intake.SourceFormat;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/** MCP tool calls. Binary content (e.g. PDF) arrives base64-encoded. */
@Component
public class McpIntakeAdapter implements IntakeAdapter<McpIngestArgs> {

    private final FormatDetector formatDetector;

    public McpIntakeAdapter(FormatDetector formatDetector) {
        this.formatDetector = formatDetector;
    }

    @Override
    public IngestionRequest adapt(McpIngestArgs source) {
        if (!StringUtils.hasText(source.name())) {
            throw new IntakeRejectedException("'name' is required");
        }
        String name = source.name().strip();
        NamedByteArrayResource content = new NamedByteArrayResource(bytes(source), name);
        SourceFormat format = formatDetector.detect(null, name, source.mimeType(), content);

        return IngestionRequest.of(IntakeChannel.MCP, name, format, content,
                source.categories(), Map.of());
    }

    private static byte[] bytes(McpIngestArgs source) {
        boolean hasText = StringUtils.hasText(source.text());
        boolean hasBase64 = StringUtils.hasText(source.base64());
        if (hasText == hasBase64) {
            throw new IntakeRejectedException("Provide exactly one of 'text' or 'base64'");
        }
        if (hasText) {
            return source.text().getBytes(StandardCharsets.UTF_8);
        }
        try {
            return Base64.getMimeDecoder().decode(source.base64());
        } catch (IllegalArgumentException e) {
            throw new IntakeRejectedException("'base64' is not valid base64", e);
        }
    }
}
