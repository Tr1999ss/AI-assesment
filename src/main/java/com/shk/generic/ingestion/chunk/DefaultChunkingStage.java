package com.shk.generic.ingestion.chunk;

import com.shk.generic.ingestion.category.CategorizedDocument;
import com.shk.generic.ingestion.category.CategorySet;
import com.shk.generic.ingestion.registry.RegisteredDocument;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Splits each section separately (so a chunk never spans two pages or headings), then gives every
 * chunk a stable id, document-wide position, category tags and its embedding formatter.
 */
@Component
public class DefaultChunkingStage implements ChunkingStage {

    private final TextChunker chunker;
    private final ChunkIdGenerator idGenerator;
    private final ChunkProperties properties;

    public DefaultChunkingStage(TextChunker chunker, ChunkIdGenerator idGenerator, ChunkProperties properties) {
        this.chunker = chunker;
        this.idGenerator = idGenerator;
        this.properties = properties;
    }

    @Override
    public ChunkedDocument chunk(CategorizedDocument document) {
        List<Document> raw = new ArrayList<>();
        for (Document section : document.extracted().sections()) {
            raw.addAll(chunker.split(section));
            if (raw.size() > properties.maxChunksPerDocument()) {
                throw new ChunkingException("'" + document.registration().filename() + "' produces more than "
                        + properties.maxChunksPerDocument() + " chunks; split it into smaller documents");
            }
        }
        if (raw.isEmpty()) {
            throw new ChunkingException("'" + document.registration().filename() + "' produced no chunks");
        }

        RegisteredDocument registration = document.registration();
        CategorySet categories = document.categories();
        List<Document> chunks = new ArrayList<>(raw.size());
        for (int index = 0; index < raw.size(); index++) {
            Document chunk = raw.get(index);
            Map<String, Object> metadata = new HashMap<>(chunk.getMetadata());
            metadata.remove(ChunkMetadata.SPLITTER_PARENT_ID);
            metadata.remove(ChunkMetadata.SPLITTER_TOTAL_CHUNKS);
            metadata.put(ChunkMetadata.DOCUMENT_ID, registration.id().toString());
            metadata.put(ChunkMetadata.CONTENT_HASH, registration.contentHash());
            metadata.put(ChunkMetadata.CATEGORIES, categories.all());
            metadata.put(ChunkMetadata.PRIMARY_CATEGORY, categories.primary());
            metadata.put(ChunkMetadata.CATEGORY_SOURCE, document.source().name());
            metadata.put(ChunkMetadata.CHUNK_INDEX, index);
            metadata.put(ChunkMetadata.CHUNK_COUNT, raw.size());

            Document tagged = Document.builder()
                    .id(idGenerator.chunkId(registration.id(), index))
                    .text(chunk.getText())
                    .metadata(metadata)
                    .build();
            tagged.setContentFormatter(ChunkEmbeddingFormatter.INSTANCE);
            chunks.add(tagged);
        }
        return new ChunkedDocument(document, chunks);
    }
}
