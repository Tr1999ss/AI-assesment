package com.shk.generic.retrieval.search;

import com.shk.generic.ingestion.chunk.ChunkMetadata;
import com.shk.generic.ingestion.extract.DefaultExtractionStage;
import com.shk.generic.retrieval.RetrievalUnavailableException;
import com.shk.generic.retrieval.SearchHit;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** Searches the Spring AI {@link VectorStore} and maps chunk metadata into {@link SearchHit}s. */
@Component
public class VectorStoreChunkSearcher implements ChunkSearcher {

    /** Written by Spring AI's PDF and Markdown readers. */
    static final String PAGE_NUMBER = "page_number";
    static final String TITLE = "title";

    private final VectorStore vectorStore;

    public VectorStoreChunkSearcher(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Override
    public List<SearchHit> search(String text, int topK, double minScore, Filter.@Nullable Expression filter) {
        SearchRequest.Builder request = SearchRequest.builder()
                .query(text)
                .topK(topK)
                .similarityThreshold(minScore);
        if (filter != null) {
            request.filterExpression(filter);
        }
        List<Document> documents;
        try {
            documents = vectorStore.similaritySearch(request.build());
        } catch (RuntimeException e) {
            throw new RetrievalUnavailableException("Search failed: " + e.getMessage(), e);
        }
        return documents.stream().map(VectorStoreChunkSearcher::toHit).toList();
    }

    static SearchHit toHit(Document document) {
        Map<String, Object> m = document.getMetadata();
        return new SearchHit(
                document.getId(),
                string(m.get(ChunkMetadata.DOCUMENT_ID), ""),
                string(m.get(DefaultExtractionStage.SOURCE), ""),
                string(m.get(TITLE), null),
                integer(m.get(PAGE_NUMBER)),
                m.get(ChunkMetadata.CATEGORIES) instanceof List<?> list
                        ? list.stream().map(String::valueOf).toList()
                        : List.of(),
                string(m.get(ChunkMetadata.PRIMARY_CATEGORY), null),
                integer(m.get(ChunkMetadata.CHUNK_INDEX)) == null ? 0 : integer(m.get(ChunkMetadata.CHUNK_INDEX)),
                document.getScore() == null ? 0.0 : document.getScore(),
                document.getText() == null ? "" : document.getText());
    }

    private static String string(@Nullable Object value, String fallback) {
        return value == null ? fallback : value.toString();
    }

    private static @Nullable Integer integer(@Nullable Object value) {
        return value instanceof Number n ? n.intValue() : null;
    }
}
