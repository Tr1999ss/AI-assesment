package com.shk.generic.ingestion.chunk;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param chunkSizeTokens       target chunk size; text-embedding-3 accepts up to 8191 tokens
 * @param minChunkSizeChars     the splitter looks for a sentence break only after this many characters
 * @param minChunkLengthToEmbed chunks shorter than this (after trimming) are discarded
 * @param maxChunksPerDocument  above this the document is rejected rather than silently truncated,
 *                              which also caps embedding cost per upload
 */
@ConfigurationProperties("ingestion.chunk")
public record ChunkProperties(
        @DefaultValue("512") int chunkSizeTokens,
        @DefaultValue("200") int minChunkSizeChars,
        @DefaultValue("5") int minChunkLengthToEmbed,
        @DefaultValue("2000") int maxChunksPerDocument
) {
}
