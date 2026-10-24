package com.shk.generic.docs;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * Read-only access to the docs loaded by {@link DocsCatalog}. Path variables are only ever map keys.
 * Responses carry ETag and Last-Modified; Spring answers matching conditional requests with 304.
 * Access rules live in {@code SecurityConfiguration} ({@code docs.public}).
 */
@RestController
@RequestMapping("/api/docs")
@ConditionalOnProperty(name = "docs.enabled", havingValue = "true", matchIfMissing = true)
public class DocsController {

    /** Private (the docs may be access-controlled), and revalidated each time so edits show after a redeploy. */
    private static final CacheControl CACHE = CacheControl.noCache().cachePrivate();

    private final DocsCatalog catalog;

    public DocsController(DocsCatalog catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    public DocsIndex index() {
        return new DocsIndex(catalog.home(), catalog.nav());
    }

    @GetMapping("/assets/{*path}")
    public ResponseEntity<byte[]> asset(@PathVariable String path) {
        DocAsset asset = catalog.asset(strip(path)).orElseThrow(DocNotFoundException::new);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(asset.contentType()))
                .cacheControl(CACHE)
                .eTag(asset.etag())
                .lastModified(asset.lastModified())
                .body(asset.content());
    }

    @GetMapping("/{*slug}")
    public ResponseEntity<DocResponse> page(@PathVariable String slug) {
        DocPage page = catalog.page(strip(slug)).orElseThrow(DocNotFoundException::new);
        return ResponseEntity.ok()
                .cacheControl(CACHE)
                .eTag(page.etag())
                .lastModified(page.lastModified())
                .body(new DocResponse(page.slug(), page.title(), page.path(), page.markdown(), page.lastModified()));
    }

    /** Capture-all variables start with '/'. */
    private static String strip(String value) {
        return value.startsWith("/") ? value.substring(1) : value;
    }

    /** @param home slug of the home page */
    public record DocsIndex(String home, List<DocsNavNode> pages) {
    }

    /** @param path site-relative file path, for resolving relative links and images */
    public record DocResponse(String slug, String title, String path, String markdown, Instant lastModified) {
    }
}
