package com.shk.generic.docs;

import java.time.Instant;

/**
 * One markdown page, loaded at startup.
 *
 * @param path site-relative file path (e.g. {@code docs/api.md}); clients resolve relative links against it
 * @param etag strong validator over the content, already quoted
 */
public record DocPage(String slug, String title, String path, String markdown, Instant lastModified, String etag) {
}
