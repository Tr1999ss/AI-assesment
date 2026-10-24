package com.shk.generic.ingestion.chunk;

import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Token-based splitting with Spring AI's {@link TokenTextSplitter} (cl100k encoding, the tokenizer
 * text-embedding-3 uses), breaking at sentence ends where possible.
 */
@Component
public class TokenTextChunker implements TextChunker {

    private final TokenTextSplitter splitter;

    public TokenTextChunker(ChunkProperties properties) {
        this.splitter = TokenTextSplitter.builder()
                .withChunkSize(properties.chunkSizeTokens())
                .withMinChunkSizeChars(properties.minChunkSizeChars())
                .withMinChunkLengthToEmbed(properties.minChunkLengthToEmbed())
                .withMaxNumChunks(Integer.MAX_VALUE)   // the stage enforces a per-document limit instead
                .withKeepSeparator(true)
                .build();
    }

    @Override
    public List<Document> split(Document section) {
        return splitter.split(section);
    }
}
