package com.shk.generic.ingestion.intake;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultFormatDetectorTest {

    private final DefaultFormatDetector detector = new DefaultFormatDetector();

    @Test
    void declaredFormatWins() {
        assertThat(detect(SourceFormat.MARKDOWN, "notes.txt", "text/plain", "{}"))
                .isEqualTo(SourceFormat.MARKDOWN);
    }

    @Test
    void pdfMagicBeatsMisleadingExtension() {
        assertThat(detect(null, "report.txt", "text/plain", "%PDF-1.7 ..."))
                .isEqualTo(SourceFormat.PDF);
    }

    @Test
    void extensionBeatsGenericBrowserContentType() {
        assertThat(detect(null, "readme.md", "application/octet-stream", "# Title"))
                .isEqualTo(SourceFormat.MARKDOWN);
    }

    @Test
    void contentTypeUsedWhenNoExtension() {
        assertThat(detect(null, "data", "application/json; charset=utf-8", "x"))
                .isEqualTo(SourceFormat.JSON);
    }

    @Test
    void sniffsJsonThenFallsBackToText() {
        assertThat(detect(null, "untitled", null, "  [1, 2]")).isEqualTo(SourceFormat.JSON);
        assertThat(detect(null, "untitled", null, "hello")).isEqualTo(SourceFormat.TEXT);
    }

    @Test
    void binaryWithoutHintsIsUnknown() {
        byte[] binary = {1, 0, 2, 3};
        assertThat(detector.detect(null, "blob", null, new NamedByteArrayResource(binary, "blob")))
                .isEqualTo(SourceFormat.UNKNOWN);
    }

    private SourceFormat detect(SourceFormat declared, String filename, String contentType, String body) {
        return detector.detect(declared, filename, contentType,
                new NamedByteArrayResource(body.getBytes(StandardCharsets.UTF_8), filename));
    }
}
