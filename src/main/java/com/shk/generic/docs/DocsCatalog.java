package com.shk.generic.docs;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The documentation set, loaded once at startup and immutable afterwards.
 * <p>
 * Security model: requests never become file paths. Pages are looked up by slug in a map built from the
 * manifest (or, without one, from the build-time copy of the docs); images only if an allowlisted page
 * references them. Anything not loaded here does not exist for the API.
 * <p>
 * Mistakes in the docs (missing file, bad slug, broken image) are collected in {@link #problems()} and logged
 * instead of failing startup; {@code DocsContentTests} fails the build on them.
 */
@Slf4j
public final class DocsCatalog {

    /** Site-relative to the docs root; repo layout: docs/_nav.json. */
    static final String MANIFEST = "docs/_nav.json";
    /** Matched case-insensitively: readme.md and README.md are both the home page. */
    private static final String DEFAULT_HOME = "README.md";

    /** Lowercase segments of letters, digits, '-' and '_', joined by '/'. "assets" is reserved for images. */
    private static final Pattern SLUG = Pattern.compile("[a-z0-9]+(?:[-_][a-z0-9]+)*(?:/[a-z0-9]+(?:[-_][a-z0-9]+)*)*");
    private static final Pattern SAFE_PATH = Pattern.compile("[A-Za-z0-9_\\-./]+");
    private static final Pattern FIRST_HEADING = Pattern.compile("^#\\s+(.+?)\\s*#*\\s*$", Pattern.MULTILINE);
    private static final Pattern FENCE = Pattern.compile("(?ms)^(```|~~~).*?^\\1\\s*$");
    private static final Pattern IMAGE = Pattern.compile("!\\[[^\\]]*]\\(\\s*<?([^\\s)>]+)>?(?:\\s+\"[^\"]*\")?\\s*\\)");
    private static final Map<String, String> IMAGE_TYPES = Map.of(
            "png", "image/png", "jpg", "image/jpeg", "jpeg", "image/jpeg", "gif", "image/gif", "webp", "image/webp");

    private final String home;
    private final List<DocsNavNode> nav;
    private final Map<String, DocPage> pages;
    private final Map<String, DocAsset> assets;
    private final List<String> problems;

    private DocsCatalog(String home, List<DocsNavNode> nav, Map<String, DocPage> pages, Map<String, DocAsset> assets,
                        List<String> problems) {
        this.home = home;
        this.nav = List.copyOf(nav);
        this.pages = Map.copyOf(pages);
        this.assets = Map.copyOf(assets);
        this.problems = List.copyOf(problems);
    }

    /** Home page slug (the README unless the manifest says otherwise). */
    public String home() {
        return home;
    }

    public List<DocsNavNode> nav() {
        return nav;
    }

    public Optional<DocPage> page(String slug) {
        return Optional.ofNullable(pages.get(slug));
    }

    public Optional<DocAsset> asset(String path) {
        return Optional.ofNullable(assets.get(path));
    }

    public Collection<DocPage> pages() {
        return pages.values();
    }

    public List<String> problems() {
        return problems;
    }

    // ---------------------------------------------------------------- loading

    public static DocsCatalog load(ResourcePatternResolver resolver, String location, JsonMapper json) {
        String root = location.endsWith("/") ? location : location + "/";
        return new Loader(resolver, root, json).load();
    }

    private static final class Loader {
        private final ResourcePatternResolver resolver;
        private final String root;
        private final JsonMapper json;
        private final Instant loadedAt = Instant.now();
        private final Map<String, DocPage> pages = new LinkedHashMap<>();
        private final Map<String, DocPage> byPath = new LinkedHashMap<>();
        private final List<String> problems = new ArrayList<>();

        Loader(ResourcePatternResolver resolver, String root, JsonMapper json) {
            this.resolver = resolver;
            this.root = root;
            this.json = json;
        }

        DocsCatalog load() {
            Resource manifest = resolver.getResource(root + MANIFEST);
            List<DocsNavNode> nav;
            String homePath;
            if (manifest.exists()) {
                JsonNode tree = json.readTree(read(manifest));
                homePath = tree.path("home").asString(DEFAULT_HOME);
                nav = entries(tree.path("pages"));
            } else {
                homePath = DEFAULT_HOME;
                nav = scan();
            }
            DocPage home = byPath.entrySet().stream()
                    .filter(e -> e.getKey().equalsIgnoreCase(homePath))
                    .map(Map.Entry::getValue).findFirst().orElse(null);
            if (home == null && !pages.isEmpty()) {
                home = pages.values().iterator().next();
                problems.add("Home page " + homePath + " not found; using " + home.path());
            }
            Map<String, DocAsset> assets = assets();
            problems.forEach(p -> log.warn("Docs: {}", p));
            log.info("Docs: {} pages, {} images from {}{}", pages.size(), assets.size(), root,
                    manifest.exists() ? " (manifest)" : " (no manifest, alphabetical)");
            return new DocsCatalog(home == null ? "" : home.slug(), nav, pages, assets, problems);
        }

        /** Manifest entries: {"file", "title"?, "slug"?, "widgets"?} or {"title", "pages": [...]} sections. */
        private List<DocsNavNode> entries(JsonNode list) {
            List<DocsNavNode> nodes = new ArrayList<>();
            for (JsonNode entry : list) {
                int order = nodes.size();
                if (entry.has("pages")) {
                    List<DocsNavNode> children = entries(entry.get("pages"));
                    nodes.add(new DocsNavNode(entry.path("title").asString("Untitled"), null, null, order, null, children));
                    continue;
                }
                String file = entry.path("file").asString("");
                DocPage page = addPage(file, entry.path("slug").asString(null), entry.path("title").asString(null));
                if (page != null) {
                    Map<String, String> widgets = new LinkedHashMap<>();
                    entry.path("widgets").properties().forEach(e -> widgets.put(e.getKey(), e.getValue().asString()));
                    nodes.add(new DocsNavNode(page.title(), page.slug(), page.path(), order, widgets, null));
                }
            }
            return nodes;
        }

        /** No manifest: the README first, then every other .md alphabetically by path. */
        private List<DocsNavNode> scan() {
            List<String> paths = new ArrayList<>();
            try {
                String base = resolver.getResource(root).getURL().toString();
                for (Resource r : resolver.getResources(root + "**/*.md")) {
                    String url = r.getURL().toString();
                    if (url.startsWith(base)) {
                        paths.add(URLDecoder.decode(url.substring(base.length()), StandardCharsets.UTF_8));
                    }
                }
            } catch (IOException e) {
                problems.add("Could not list docs under " + root + ": " + e.getMessage());
            }
            paths.removeIf(p -> p.contains("/_") || p.startsWith("_") || p.contains("/."));
            paths.sort(Comparator.comparing((String p) -> !p.equalsIgnoreCase(DEFAULT_HOME))
                    .thenComparing(p -> p.toLowerCase(Locale.ROOT)));
            List<DocsNavNode> nodes = new ArrayList<>();
            for (String path : paths) {
                DocPage page = addPage(path, null, null);
                if (page != null) {
                    nodes.add(new DocsNavNode(page.title(), page.slug(), page.path(), nodes.size(), null, null));
                }
            }
            return nodes;
        }

        private @Nullable DocPage addPage(String file, @Nullable String slug, @Nullable String title) {
            String path = normalize("", file);
            if (path == null || !path.toLowerCase(Locale.ROOT).endsWith(".md")) {
                problems.add("Skipped '" + file + "': only .md files inside the docs root can be pages");
                return null;
            }
            if (byPath.containsKey(path)) {
                problems.add("Skipped duplicate entry for " + path);
                return null;
            }
            Resource resource = resolver.getResource(root + path);
            if (!resource.exists()) {
                problems.add("Listed page " + path + " does not exist");
                return null;
            }
            String id = slug != null ? slug : slugFor(path);
            if (!SLUG.matcher(id).matches() || id.equals("assets") || id.startsWith("assets/")) {
                problems.add("Skipped " + path + ": invalid slug '" + id + "'");
                return null;
            }
            if (pages.containsKey(id)) {
                problems.add("Skipped " + path + ": slug '" + id + "' already used by " + pages.get(id).path());
                return null;
            }
            String markdown = read(resource);
            String pageTitle = title != null && !title.isBlank() ? title : titleOf(markdown, path);
            DocPage page = new DocPage(id, pageTitle, path, markdown, lastModified(resource), etag(markdown.getBytes(StandardCharsets.UTF_8)));
            pages.put(id, page);
            byPath.put(path, page);
            return page;
        }

        /** Images referenced by loaded pages, resolved relative to each page; nothing else is served. */
        private Map<String, DocAsset> assets() {
            Map<String, DocAsset> assets = new LinkedHashMap<>();
            for (DocPage page : pages.values()) {
                for (String ref : imageRefs(page.markdown())) {
                    String path = resolveRelative(page.path(), ref);
                    if (path == null) {
                        continue; // absolute URL: not ours to serve
                    }
                    String type = IMAGE_TYPES.get(extension(path));
                    if (type == null) {
                        problems.add(page.path() + " references '" + ref + "', which is not a supported image type");
                        continue;
                    }
                    if (assets.containsKey(path)) {
                        continue;
                    }
                    Resource resource = resolver.getResource(root + path);
                    if (!resource.exists()) {
                        problems.add(page.path() + " references missing image " + path);
                        continue;
                    }
                    try {
                        byte[] bytes = resource.getContentAsByteArray();
                        assets.put(path, new DocAsset(path, bytes, type, lastModified(resource), etag(bytes)));
                    } catch (IOException e) {
                        problems.add("Could not read image " + path + ": " + e.getMessage());
                    }
                }
            }
            return assets;
        }

        private String read(Resource resource) {
            try {
                return resource.getContentAsString(StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException("Could not read docs file " + resource, e);
            }
        }

        private Instant lastModified(Resource resource) {
            try {
                long millis = resource.lastModified();
                return millis > 0 ? Instant.ofEpochMilli(millis) : loadedAt;
            } catch (IOException e) {
                return loadedAt;
            }
        }
    }

    // ---------------------------------------------------------------- helpers (package-private for tests)

    /** readme.md → readme, docs/api.md → api, docs/guides/setup.md → guides/setup. */
    static String slugFor(String path) {
        String noExt = path.substring(0, path.length() - 3);
        String trimmed = noExt.startsWith("docs/") ? noExt.substring(5) : noExt;
        return trimmed.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/_-]+", "-");
    }

    static String titleOf(String markdown, String path) {
        Matcher m = FIRST_HEADING.matcher(FENCE.matcher(markdown).replaceAll(""));
        if (m.find()) {
            return m.group(1).replaceAll("[*_`]", "").trim();
        }
        String name = path.substring(path.lastIndexOf('/') + 1, path.length() - 3);
        return name.isEmpty() ? path : Character.toUpperCase(name.charAt(0)) + name.substring(1).replace('-', ' ');
    }

    static List<String> imageRefs(String markdown) {
        List<String> refs = new ArrayList<>();
        Matcher m = IMAGE.matcher(FENCE.matcher(markdown).replaceAll(""));
        while (m.find()) {
            refs.add(m.group(1));
        }
        return refs;
    }

    /** A link target relative to {@code fromPath}, as a normalized site path; null for absolute URLs or escapes. */
    static @Nullable String resolveRelative(String fromPath, String ref) {
        if (ref.isEmpty() || ref.startsWith("/") || ref.startsWith("#") || ref.matches("^[a-zA-Z][a-zA-Z0-9+.-]*:.*")) {
            return null;
        }
        String clean = ref.replaceAll("[?#].*$", "");
        String dir = fromPath.contains("/") ? fromPath.substring(0, fromPath.lastIndexOf('/')) : "";
        return normalize(dir, URLDecoder.decode(clean, StandardCharsets.UTF_8));
    }

    /** Joins and normalizes path segments; null if the result would leave the docs root or has odd characters. */
    static @Nullable String normalize(String dir, String path) {
        if (path.isEmpty() || path.startsWith("/") || path.contains("\\") || !SAFE_PATH.matcher(path).matches()) {
            return null;
        }
        Deque<String> stack = new ArrayDeque<>();
        for (String segment : (dir.isEmpty() ? path : dir + "/" + path).split("/")) {
            if (segment.isEmpty() || segment.equals(".")) {
                continue;
            }
            if (segment.equals("..")) {
                if (stack.isEmpty()) {
                    return null;
                }
                stack.removeLast();
            } else {
                stack.addLast(segment);
            }
        }
        return stack.isEmpty() ? null : String.join("/", stack);
    }

    private static String extension(String path) {
        int dot = path.lastIndexOf('.');
        return dot < 0 ? "" : path.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String etag(byte[] content) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(content);
            return "\"" + HexFormat.of().formatHex(hash, 0, 16) + "\"";
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
