package com.shk.generic.ingestion.chunk;

/**
 * Metadata keys every stored chunk carries. These are the vector store's filter vocabulary,
 * e.g. {@code categories == 'hr'} or {@code document_id == '...'}.
 * Section-level keys from extraction ({@code source}, {@code format}, {@code section_index},
 * {@code page_number}, {@code title}, ...) are kept as well.
 */
public final class ChunkMetadata {

    public static final String DOCUMENT_ID = "document_id";
    public static final String CONTENT_HASH = "content_hash";
    /** JSON array, primary first. */
    public static final String CATEGORIES = "categories";
    public static final String PRIMARY_CATEGORY = "primary_category";
    public static final String CATEGORY_SOURCE = "category_source";
    /** Position within the whole document, 0-based and contiguous across sections. */
    public static final String CHUNK_INDEX = "chunk_index";
    public static final String CHUNK_COUNT = "chunk_count";

    /** Added by Spring AI's splitter; per-section and random, so replaced by the keys above. */
    static final String SPLITTER_PARENT_ID = "parent_document_id";
    static final String SPLITTER_TOTAL_CHUNKS = "total_chunks";

    private ChunkMetadata() {
    }
}
