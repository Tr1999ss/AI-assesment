package com.shk.generic.ingestion.chunk;

import com.shk.generic.ingestion.category.CategorizedDocument;
import com.shk.generic.ingestion.category.CategorySet;
import com.shk.generic.ingestion.category.CategorySource;
import com.shk.generic.ingestion.extract.DefaultExtractionStage;
import com.shk.generic.ingestion.extract.ExtractedDocument;
import com.shk.generic.ingestion.intake.IngestionRequest;
import com.shk.generic.ingestion.intake.IntakeChannel;
import com.shk.generic.ingestion.intake.NamedByteArrayResource;
import com.shk.generic.ingestion.intake.SourceFormat;
import com.shk.generic.ingestion.registry.RegisteredDocument;
import com.shk.generic.ingestion.registry.RegistrationStatus;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.MetadataMode;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultChunkingStageTest {

    /** Small chunks so a few paragraphs split into several. */
    private static final ChunkProperties SMALL = new ChunkProperties(40, 50, 5, 100);

    private final DefaultChunkingStage stage = stage(SMALL);

    @Test
    void chunksAreIndexedAcrossSectionsAndNeverSpanTwoSections() {
        ChunkedDocument result = stage.chunk(categorized(UUID.randomUUID(),
                section(0, Map.of("page_number", 1), longText("Leave", 12)),
                section(1, Map.of("page_number", 2), longText("Expenses", 12))));

        List<Document> chunks = result.chunks();
        assertThat(chunks).hasSizeGreaterThan(2);
        assertThat(chunks).extracting(c -> c.getMetadata().get(ChunkMetadata.CHUNK_INDEX))
                .containsExactlyElementsOf(IntStream.range(0, chunks.size()).boxed().toList());
        assertThat(chunks).allSatisfy(c -> assertThat(c.getMetadata())
                .containsEntry(ChunkMetadata.CHUNK_COUNT, chunks.size()));
        assertThat(chunks).allSatisfy(c -> {
            boolean page1 = c.getText().contains("Leave");
            boolean page2 = c.getText().contains("Expenses");
            assertThat(page1 ^ page2).as("chunk mixes sections: %s", c.getText()).isTrue();
            assertThat(c.getMetadata()).containsEntry("page_number", page1 ? 1 : 2);
        });
    }

    @Test
    void everyChunkIsTaggedWithDocumentAndOrderedCategories() {
        UUID documentId = UUID.randomUUID();
        ChunkedDocument result = stage.chunk(categorized(documentId, section(0, Map.of(), longText("Policy", 3))));

        assertThat(result.chunks()).allSatisfy(c -> assertThat(c.getMetadata())
                .containsEntry(ChunkMetadata.DOCUMENT_ID, documentId.toString())
                .containsEntry(ChunkMetadata.CONTENT_HASH, "a".repeat(64))
                .containsEntry(ChunkMetadata.CATEGORIES, List.of("legal", "finance"))
                .containsEntry(ChunkMetadata.PRIMARY_CATEGORY, "legal")
                .containsEntry(ChunkMetadata.CATEGORY_SOURCE, "AI")
                .containsEntry(DefaultExtractionStage.SOURCE, "policy.md")
                .doesNotContainKeys("parent_document_id", "total_chunks"));
    }

    @Test
    void chunkIdsAreStablePerDocumentAndPosition() {
        UUID documentId = UUID.randomUUID();
        Document section = section(0, Map.of(), longText("Policy", 8));

        List<String> first = ids(stage.chunk(categorized(documentId, section)));
        List<String> again = ids(stage.chunk(categorized(documentId, section)));
        List<String> other = ids(stage.chunk(categorized(UUID.randomUUID(), section)));

        assertThat(first).doesNotHaveDuplicates().containsExactlyElementsOf(again);
        assertThat(first).doesNotContainAnyElementsOf(other);
        assertThat(first).allSatisfy(id -> assertThat(UUID.fromString(id)).isNotNull());
    }

    @Test
    void embeddingTextHasContextHeaderButNoIdsOrCategories() {
        Document section = section(0, Map.of("title", "Annual leave"), "Twenty days per year.");
        Document chunk = stage.chunk(categorized(UUID.randomUUID(), section)).chunks().get(0);

        String embedded = chunk.getFormattedContent(MetadataMode.EMBED);
        assertThat(embedded).isEqualTo("Document: policy.md\nSection: Annual leave\n\nTwenty days per year.");
        assertThat(chunk.getFormattedContent(MetadataMode.NONE)).isEqualTo("Twenty days per year.");
    }

    @Test
    void tooManyChunksIsRejectedNotTruncated() {
        DefaultChunkingStage tight = stage(new ChunkProperties(40, 50, 5, 2));

        assertThatThrownBy(() -> tight.chunk(categorized(UUID.randomUUID(), section(0, Map.of(), longText("Big", 20)))))
                .isInstanceOf(ChunkingException.class)
                .hasMessageContaining("more than 2 chunks");
    }

    @Test
    void shortSectionStillBecomesOneChunk() {
        ChunkedDocument result = stage.chunk(categorized(UUID.randomUUID(), section(0, Map.of(), "Short note.")));

        assertThat(result.chunks()).singleElement()
                .satisfies(c -> assertThat(c.getText()).isEqualTo("Short note."));
    }

    private static DefaultChunkingStage stage(ChunkProperties properties) {
        return new DefaultChunkingStage(new TokenTextChunker(properties), new DeterministicChunkIdGenerator(), properties);
    }

    private static List<String> ids(ChunkedDocument doc) {
        return doc.chunks().stream().map(Document::getId).toList();
    }

    private static String longText(String topic, int sentences) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sentences; i++) {
            sb.append(topic).append(" rule number ").append(i)
                    .append(" applies to every employee in every office without exception. ");
        }
        return sb.toString().strip();
    }

    private static Document section(int index, Map<String, Object> extra, String text) {
        Map<String, Object> metadata = new java.util.HashMap<>(extra);
        metadata.put(DefaultExtractionStage.SOURCE, "policy.md");
        metadata.put(DefaultExtractionStage.SECTION_INDEX, index);
        return new Document(text, metadata);
    }

    private static CategorizedDocument categorized(UUID documentId, Document... sections) {
        IngestionRequest request = IngestionRequest.of(IntakeChannel.JSON_TEXT, "policy.md", SourceFormat.MARKDOWN,
                new NamedByteArrayResource(new byte[]{1}, "policy.md"), List.of(), Map.of());
        ExtractedDocument extracted = new ExtractedDocument(request, List.of(sections), "ignored");
        RegisteredDocument registration = new RegisteredDocument(documentId, "a".repeat(64), "policy.md",
                SourceFormat.MARKDOWN, IntakeChannel.JSON_TEXT, List.of(), null, null, null, RegistrationStatus.PENDING,
                Instant.now(), Instant.now());
        return new CategorizedDocument(extracted, registration,
                CategorySet.of("legal", List.of("finance")), CategorySource.AI);
    }
}
