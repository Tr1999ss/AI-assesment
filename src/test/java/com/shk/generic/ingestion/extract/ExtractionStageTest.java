package com.shk.generic.ingestion.extract;

import com.shk.generic.ingestion.extract.reader.JsonExtractor;
import com.shk.generic.ingestion.extract.reader.MarkdownExtractor;
import com.shk.generic.ingestion.extract.reader.PdfExtractor;
import com.shk.generic.ingestion.extract.reader.PlainTextExtractor;
import com.shk.generic.ingestion.intake.IngestionRequest;
import com.shk.generic.ingestion.intake.IntakeChannel;
import com.shk.generic.ingestion.intake.IntakeProperties;
import com.shk.generic.ingestion.intake.NamedByteArrayResource;
import com.shk.generic.ingestion.intake.SourceFormat;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.util.unit.DataSize;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExtractionStageTest {

    private static final IntakeProperties ALL_FORMATS = new IntakeProperties(DataSize.ofMegabytes(1),
            EnumSet.of(SourceFormat.PDF, SourceFormat.MARKDOWN, SourceFormat.JSON, SourceFormat.TEXT));

    private final ExtractionStage stage = new DefaultExtractionStage(
            new RegistryExtractorResolver(List.of(new PdfExtractor(), new MarkdownExtractor(),
                    new JsonExtractor(JsonMapper.shared()), new PlainTextExtractor()), ALL_FORMATS),
            new WhitespaceTextNormalizer());

    @Test
    void pdfYieldsOneSectionPerPage() throws IOException {
        ExtractedDocument doc = stage.extract(request(SourceFormat.PDF, pdf("First page", "Second page")));

        assertThat(doc.sections()).hasSize(2);
        assertThat(doc.sections().get(1).getMetadata())
                .containsEntry("page_number", 2)
                .containsEntry(DefaultExtractionStage.SOURCE, "doc")
                .containsEntry(DefaultExtractionStage.SECTION_INDEX, 1);
        assertThat(doc.normalizedText()).contains("First page").contains("Second page");
    }

    @Test
    void markdownCategoryMetadataIsRenamed() {
        String md = "# Leave policy\n\nTwenty days per year.\n\n# Expenses\n\nSubmit receipts monthly.";
        ExtractedDocument doc = stage.extract(request(SourceFormat.MARKDOWN, md.getBytes(StandardCharsets.UTF_8)));

        assertThat(doc.sections()).isNotEmpty();
        assertThat(doc.sections()).allSatisfy(s -> assertThat(s.getMetadata()).doesNotContainKey("category"));
        assertThat(doc.sections()).anySatisfy(s ->
                assertThat(s.getMetadata()).containsEntry(MarkdownExtractor.MD_ELEMENT, "header_1"));
    }

    @Test
    void jsonArrayIsOneSectionPerElementFlattened() {
        String json = "[{\"plan\":\"pro\",\"limits\":{\"seats\":10}}, \"plain\", null]";
        ExtractedDocument doc = stage.extract(request(SourceFormat.JSON, json.getBytes(StandardCharsets.UTF_8)));

        assertThat(doc.sections()).extracting(Document::getText)
                .containsExactly("plan: pro\nlimits.seats: 10", "plain");
    }

    @Test
    void normalizedTextIgnoresWhitespaceAndLineEndingDifferences() {
        String lf = stage.extract(request(SourceFormat.TEXT, "a  b\nc".getBytes(StandardCharsets.UTF_8))).normalizedText();
        String crlf = stage.extract(request(SourceFormat.TEXT, "a b\r\n\r\nc  ".getBytes(StandardCharsets.UTF_8))).normalizedText();

        assertThat(lf).isEqualTo(crlf).isEqualTo("a b c");
    }

    @Test
    void blankContentIsRejected() {
        assertThatThrownBy(() -> stage.extract(request(SourceFormat.TEXT, "  \n ".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(ExtractionException.class);
    }

    @Test
    void corruptPdfBecomesExtractionException() {
        assertThatThrownBy(() -> stage.extract(request(SourceFormat.PDF, "%PDF-garbage".getBytes(StandardCharsets.US_ASCII))))
                .isInstanceOf(ExtractionException.class);
    }

    @Test
    void startupFailsWhenAllowedFormatHasNoExtractor() {
        assertThatThrownBy(() -> new RegistryExtractorResolver(List.of(new PlainTextExtractor()), ALL_FORMATS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no DocumentExtractor");
    }

    private static IngestionRequest request(SourceFormat format, byte[] bytes) {
        return IngestionRequest.of(IntakeChannel.JSON_TEXT, "doc", format,
                new NamedByteArrayResource(bytes, "doc"), Set.of(), null);
    }

    private static byte[] pdf(String... pages) throws IOException {
        try (PDDocument pdf = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (String text : pages) {
                PDPage page = new PDPage();
                pdf.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(pdf, page)) {
                    cs.beginText();
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    cs.newLineAtOffset(72, 700);
                    cs.showText(text);
                    cs.endText();
                }
            }
            pdf.save(out);
            return out.toByteArray();
        }
    }
}
