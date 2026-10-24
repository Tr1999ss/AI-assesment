package com.shk.generic.ingestion.dedup;

import com.shk.generic.ingestion.extract.ExtractedDocument;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Hashes the normalized extracted text, not the raw bytes, so a re-exported PDF or a
 * CRLF/LF variant of the same Markdown counts as the same document.
 */
@Component
public class Sha256ContentHasher implements ContentHasher {

    @Override
    public String hash(ExtractedDocument document) {
        return sha256(document.normalizedText());
    }

    static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every JVM", e);
        }
    }
}
