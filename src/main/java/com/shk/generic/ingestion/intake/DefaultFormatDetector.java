package com.shk.generic.ingestion.intake;

import org.jspecify.annotations.Nullable;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Detection order, most to least trustworthy:
 * declared → PDF magic bytes → file extension → content-type → content sniffing.
 * Extension beats content-type because browsers commonly send {@code .md} as
 * {@code text/plain} or {@code application/octet-stream}.
 */
@Component
public class DefaultFormatDetector implements FormatDetector {

    private static final int SNIFF_BYTES = 1024;
    private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    @Override
    public SourceFormat detect(@Nullable SourceFormat declared, @Nullable String filename,
                               @Nullable String contentType, Resource content) {
        if (declared != null && declared != SourceFormat.UNKNOWN) {
            return declared;
        }
        byte[] head = head(content);
        if (startsWith(head, PDF_MAGIC)) {
            return SourceFormat.PDF;
        }
        return SourceFormat.fromFilename(filename)
                .or(() -> SourceFormat.fromMimeType(contentType))
                .orElseGet(() -> sniff(head));
    }

    private static SourceFormat sniff(byte[] head) {
        if (head.length == 0 || looksBinary(head)) {
            return SourceFormat.UNKNOWN;
        }
        String text = new String(head, StandardCharsets.UTF_8).stripLeading();
        return text.startsWith("{") || text.startsWith("[") ? SourceFormat.JSON : SourceFormat.TEXT;
    }

    private static boolean looksBinary(byte[] head) {
        for (byte b : head) {
            if (b == 0) {
                return true;
            }
        }
        return false;
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static byte[] head(Resource content) {
        try (InputStream in = content.getInputStream()) {
            return in.readNBytes(SNIFF_BYTES);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read content for format detection", e);
        }
    }
}
