package com.shk.generic.docs;

import java.time.Instant;

/** An image referenced by an allowlisted page, loaded at startup. */
public record DocAsset(String path, byte[] content, String contentType, Instant lastModified, String etag) {
}
