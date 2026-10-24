package com.shk.generic.ingestion.store;

import com.shk.generic.ingestion.chunk.ChunkMetadata;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/** Spring AI {@link VectorStore} (pgvector here): one table, categories in JSONB metadata. */
@Component
public class VectorStoreChunkStore implements ChunkStore {

    private final VectorStore vectorStore;

    public VectorStoreChunkStore(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Override
    public void write(List<Document> chunks) {
        vectorStore.add(chunks);
    }

    @Override
    public void deleteByDocumentId(UUID documentId) {
        vectorStore.delete(new FilterExpressionBuilder().eq(ChunkMetadata.DOCUMENT_ID, documentId.toString()).build());
    }
}
