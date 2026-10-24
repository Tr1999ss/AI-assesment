package com.shk.generic.retrieval.search;

import com.shk.generic.retrieval.RetrievalUnavailableException;
import com.shk.generic.retrieval.SearchHit;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.stereotype.Component;

import java.util.List;

/** The semantic leg: searches the Spring AI {@link VectorStore} (cosine similarity). */
@Component
public class VectorStoreChunkSearcher implements ChunkSearcher {

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
        return documents.stream().map(ChunkHitMapper::toHit).toList();
    }
}
