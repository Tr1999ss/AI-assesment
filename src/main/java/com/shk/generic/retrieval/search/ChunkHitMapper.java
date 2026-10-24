package com.shk.generic.retrieval.search;

import com.shk.generic.ingestion.chunk.ChunkMetadata;
import com.shk.generic.ingestion.extract.DefaultExtractionStage;
import com.shk.generic.retrieval.SearchHit;
import com.shk.generic.retrieval.pipeline.ScoreType;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

/** Maps a stored chunk (id, text, metadata) to a {@link SearchHit}. Shared by every retriever so hits look alike. */
public final class ChunkHitMapper {

    /** Written by Spring AI's PDF and Markdown readers. */
    static final String PAGE_NUMBER = "page_number";
    static final String TITLE = "title";

    private ChunkHitMapper() {
    }

    public static SearchHit toHit(Document document) {
        return toHit(document.getId(), document.getText(), document.getMetadata(),
                document.getScore() == null ? 0.0 : document.getScore(), ScoreType.COSINE);
    }

    public static SearchHit toHit(String id, @Nullable String text, Map<String, Object> m, double score, ScoreType type) {
        Integer chunkIndex = integer(m.get(ChunkMetadata.CHUNK_INDEX));
        return new SearchHit(
                id,
                string(m.get(ChunkMetadata.DOCUMENT_ID), ""),
                string(m.get(DefaultExtractionStage.SOURCE), ""),
                string(m.get(TITLE), null),
                integer(m.get(PAGE_NUMBER)),
                m.get(ChunkMetadata.CATEGORIES) instanceof List<?> list
                        ? list.stream().map(String::valueOf).toList()
                        : List.of(),
                string(m.get(ChunkMetadata.PRIMARY_CATEGORY), null),
                chunkIndex == null ? 0 : chunkIndex,
                score,
                text == null ? "" : text,
                type,
                null);
    }

    private static String string(@Nullable Object value, String fallback) {
        return value == null ? fallback : value.toString();
    }

    private static @Nullable Integer integer(@Nullable Object value) {
        return value instanceof Number n ? n.intValue() : null;
    }
}
