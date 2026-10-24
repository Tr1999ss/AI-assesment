package com.shk.generic.docs;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks the real docs set (README.md + docs/**, as copied by the build): every listed page loads, every relative
 * link points at a served page, every #anchor names a heading on its target page, and every image exists.
 * Mermaid syntax is parsed by the frontend's docs check (npm test in the console), which uses the real Mermaid parser.
 */
class DocsContentTests {

    private static final Pattern FENCE = Pattern.compile("(?ms)^(`{3,}|~{3,}).*?^\\1\\s*$");
    private static final Pattern INLINE_CODE = Pattern.compile("`[^`\n]*`");
    private static final Pattern LINK = Pattern.compile("(?<!!)\\[([^\\]]*)]\\(\\s*<?([^\\s)>]+)>?(?:\\s+\"[^\"]*\")?\\s*\\)");
    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.+?)\\s*#*\\s*$", Pattern.MULTILINE);

    private static DocsCatalog catalog;
    private static final Map<String, DocPage> byPath = new HashMap<>();
    private static final Map<String, Set<String>> anchors = new HashMap<>();

    @BeforeAll
    static void load() {
        catalog = DocsCatalog.load(new PathMatchingResourcePatternResolver(), "classpath:docs-site/", JsonMapper.builder().build());
        for (DocPage page : catalog.pages()) {
            byPath.put(page.path(), page);
            anchors.put(page.path(), headingAnchors(page.markdown()));
        }
    }

    @Test
    void docsSetLoadsWithoutProblems() {
        assertThat(catalog.problems()).as("problems reported while loading the docs").isEmpty();
        assertThat(catalog.page(catalog.home())).isPresent();
        assertThat(catalog.pages()).hasSizeGreaterThan(1);
    }

    @Test
    void everyRelativeLinkAndAnchorResolves() {
        List<String> broken = new ArrayList<>();
        for (DocPage page : catalog.pages()) {
            Matcher m = LINK.matcher(proseOf(page.markdown()));
            while (m.find()) {
                String href = m.group(2);
                if (href.matches("^(https?|mailto):.*")) {
                    continue;
                }
                String target = href.startsWith("#") ? page.path() : DocsCatalog.resolveRelative(page.path(), href);
                if (target == null || !byPath.containsKey(target)) {
                    broken.add(page.path() + ": [" + m.group(1) + "](" + href + ") → not a served page");
                    continue;
                }
                int hash = href.indexOf('#');
                if (hash >= 0 && !anchors.get(target).contains(href.substring(hash + 1))) {
                    broken.add(page.path() + ": [" + m.group(1) + "](" + href + ") → no such heading in " + target);
                }
            }
        }
        assertThat(broken).as("broken links").isEmpty();
    }

    @Test
    void everyReferencedImageIsServed() {
        for (DocPage page : catalog.pages()) {
            for (String ref : DocsCatalog.imageRefs(page.markdown())) {
                String path = DocsCatalog.resolveRelative(page.path(), ref);
                if (path != null) {
                    assertThat(catalog.asset(path)).as(page.path() + " → " + ref).isPresent();
                }
            }
        }
    }

    @Test
    void mermaidFencesAreClosedAndNotEmpty() {
        for (DocPage page : catalog.pages()) {
            Matcher m = Pattern.compile("(?ms)^```mermaid\\s*\\n(.*?)^```\\s*$").matcher(page.markdown());
            int opened = page.markdown().split("(?m)^```mermaid", -1).length - 1;
            int closed = 0;
            while (m.find()) {
                closed++;
                assertThat(m.group(1).trim()).as("empty mermaid block in " + page.path()).isNotEmpty();
            }
            assertThat(closed).as("unclosed mermaid fence in " + page.path()).isEqualTo(opened);
        }
    }

    /** Markdown with code removed, so code samples are never mistaken for links or headings. */
    private static String proseOf(String markdown) {
        return INLINE_CODE.matcher(FENCE.matcher(markdown).replaceAll("")).replaceAll("");
    }

    /** GitHub-style anchors (github-slugger, as the viewer uses), deduplicated with -1, -2, ... */
    static Set<String> headingAnchors(String markdown) {
        Set<String> result = new HashSet<>();
        Map<String, Integer> seen = new HashMap<>();
        Matcher m = HEADING.matcher(FENCE.matcher(markdown).replaceAll(""));
        while (m.find()) {
            String text = m.group(2)
                    .replaceAll("\\[([^\\]]*)]\\([^)]*\\)", "$1") // links → their text
                    .replaceAll("[`*]", "");                       // code and emphasis markers
            String slug = text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{M}\\p{N}\\p{Pc} -]", "").replace(' ', '-');
            int n = seen.merge(slug, 1, Integer::sum) - 1;
            result.add(n == 0 ? slug : slug + "-" + n);
        }
        return result;
    }

    @Test
    void anchorsFollowGithubRules() {
        assertThat(headingAnchors("## `GET /api/search`\n## C3. Ask (`POST /api/ask`) and the \"I don't know\" guard\n## Dup\n## Dup"))
                .contains("get-apisearch", "c3-ask-post-apiask-and-the-i-dont-know-guard", "dup", "dup-1");
    }
}
