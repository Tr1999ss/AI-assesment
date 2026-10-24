package com.shk.generic.ingestion.intake;

import org.springframework.core.io.ByteArrayResource;

/**
 * In-memory resource that keeps its filename, so later stages (hashing, extraction)
 * can re-read it and readers can still report the source name.
 */
public class NamedByteArrayResource extends ByteArrayResource {

    private final String filename;

    public NamedByteArrayResource(byte[] bytes, String filename) {
        super(bytes, filename);
        this.filename = filename;
    }

    @Override
    public String getFilename() {
        return filename;
    }
}
