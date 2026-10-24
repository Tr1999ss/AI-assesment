package com.shk.generic.ingestion.intake;

import com.shk.generic.ingestion.intake.mcp.McpIngestArgs;
import com.shk.generic.ingestion.intake.mcp.McpIntakeAdapter;
import com.shk.generic.ingestion.intake.multipart.MultipartIntakeAdapter;
import com.shk.generic.ingestion.intake.multipart.MultipartUpload;
import com.shk.generic.ingestion.intake.text.JsonTextIntakeAdapter;
import com.shk.generic.ingestion.intake.text.TextIngestPayload;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IntakeAdaptersTest {

    private final FormatDetector detector = new DefaultFormatDetector();

    @Test
    void multipartPdfIsNormalized() {
        var file = new MockMultipartFile("file", "Handbook.pdf", "application/pdf",
                "%PDF-1.4 body".getBytes(StandardCharsets.US_ASCII));

        IngestionRequest request = new MultipartIntakeAdapter(detector)
                .adapt(new MultipartUpload(file, Set.of(" HR ", "")));

        assertThat(request.channel()).isEqualTo(IntakeChannel.MULTIPART);
        assertThat(request.format()).isEqualTo(SourceFormat.PDF);
        assertThat(request.filename()).isEqualTo("Handbook.pdf");
        assertThat(request.content().getFilename()).isEqualTo("Handbook.pdf");
        assertThat(request.requestedCategories()).containsExactly("hr");
    }

    @Test
    void textPayloadGetsSyntheticFilenameFromDetectedFormat() {
        IngestionRequest request = new JsonTextIntakeAdapter(detector)
                .adapt(new TextIngestPayload("Pricing", "{\"plan\":\"pro\"}", null, null, null));

        assertThat(request.format()).isEqualTo(SourceFormat.JSON);
        assertThat(request.filename()).isEqualTo("Pricing.json");
        assertThat(request.hasRequestedCategories()).isFalse();
    }

    @Test
    void mcpDecodesBase64() {
        String pdf = Base64.getEncoder().encodeToString("%PDF-1.4".getBytes(StandardCharsets.US_ASCII));

        IngestionRequest request = new McpIntakeAdapter(detector)
                .adapt(new McpIngestArgs("spec", null, pdf, null, List.of("Engineering")));

        assertThat(request.channel()).isEqualTo(IntakeChannel.MCP);
        assertThat(request.format()).isEqualTo(SourceFormat.PDF);
        assertThat(request.requestedCategories()).containsExactly("engineering");
    }

    @Test
    void mcpRequiresExactlyOneContentField() {
        var adapter = new McpIntakeAdapter(detector);
        assertThatThrownBy(() -> adapter.adapt(new McpIngestArgs("x", "a", "YQ==", null, null)))
                .isInstanceOf(IntakeRejectedException.class);
        assertThatThrownBy(() -> adapter.adapt(new McpIngestArgs("x", null, null, null, null)))
                .isInstanceOf(IntakeRejectedException.class);
    }

    @Test
    void validatorRejectsOversizedAndDisallowedFormats() {
        var validator = new DefaultIntakeValidator(
                new IntakeProperties(DataSize.ofBytes(4), EnumSet.of(SourceFormat.TEXT)));

        assertThatThrownBy(() -> validator.validate(request(SourceFormat.TEXT, "too long")))
                .hasMessageContaining("limit");
        assertThatThrownBy(() -> validator.validate(request(SourceFormat.JSON, "{}")))
                .hasMessageContaining("not allowed");
        validator.validate(request(SourceFormat.TEXT, "ok"));
    }

    private static IngestionRequest request(SourceFormat format, String body) {
        return IngestionRequest.of(IntakeChannel.JSON_TEXT, "t", format,
                new NamedByteArrayResource(body.getBytes(StandardCharsets.UTF_8), "t"), Set.of(), null);
    }
}
