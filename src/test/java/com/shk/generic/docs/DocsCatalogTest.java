package com.shk.generic.docs;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/** Loading rules: manifest validation, fallback order, and path handling that can never leave the docs root. */
class DocsCatalogTest {

    private static DocsCatalog load(String fixture) {
        return DocsCatalog.load(new PathMatchingResourcePatternResolver(), "classpath:docs-fixtures/" + fixture + "/",
                JsonMapper.builder().build());
    }

    @Test
    void withoutManifestReadmeComesFirstThenAlphabeticalWithTitlesFromFirstHeading() {
        DocsCatalog catalog = load("no-manifest");

        assertThat(catalog.home()).isEqualTo("readme");
        assertThat(catalog.nav()).extracting(DocsNavNode::slug).containsExactly("readme", "b", "guides/a");
        assertThat(catalog.nav()).extracting(DocsNavNode::title).containsExactly("Fixture home", "Bravo page", "Alpha guide");
        assertThat(catalog.nav()).extracting(DocsNavNode::order).containsExactly(0, 1, 2);
        assertThat(catalog.page("draft")).isEmpty();
        assertThat(catalog.page("_draft")).isEmpty();
        assertThat(catalog.problems()).isEmpty();
    }

    @Test
    void manifestEntriesThatEscapeTheRootOrAreNotMarkdownAreSkippedAndReported() {
        DocsCatalog catalog = load("bad-manifest");

        assertThat(catalog.pages()).extracting(DocPage::slug).containsExactlyInAnyOrder("readme", "ok");
        assertThat(catalog.problems()).anyMatch(p -> p.contains("'../pom.xml'"))
                .anyMatch(p -> p.contains("'docs/../../secret.md'"))
                .anyMatch(p -> p.contains("'secret.txt'"))
                .anyMatch(p -> p.contains("docs/missing.md does not exist"))
                .anyMatch(p -> p.contains("invalid slug 'assets/ok'"))
                .anyMatch(p -> p.contains("invalid slug '../ok'"))
                .anyMatch(p -> p.contains("duplicate entry for docs/ok.md"))
                .anyMatch(p -> p.contains("missing image nope.png"))
                .anyMatch(p -> p.contains("'evil.svg', which is not a supported image type"));
        assertThat(catalog.asset("nope.png")).isEmpty();
        assertThat(catalog.asset("evil.svg")).isEmpty();
    }

    @Test
    void normalizeRejectsEverythingThatCouldLeaveTheRoot() {
        assertThat(DocsCatalog.normalize("", "docs/api.md")).isEqualTo("docs/api.md");
        assertThat(DocsCatalog.normalize("docs", "../README.md")).isEqualTo("README.md");
        assertThat(DocsCatalog.normalize("docs", "./guides/../api.md")).isEqualTo("docs/api.md");
        assertThat(DocsCatalog.normalize("", "../secret.md")).isNull();
        assertThat(DocsCatalog.normalize("docs", "../../secret.md")).isNull();
        assertThat(DocsCatalog.normalize("", "/etc/passwd")).isNull();
        assertThat(DocsCatalog.normalize("", "..\\secret.md")).isNull();
        assertThat(DocsCatalog.normalize("", "C:/Windows/win.ini")).isNull();
        assertThat(DocsCatalog.normalize("", "docs/%2e%2e/x.md")).isNull();
        assertThat(DocsCatalog.normalize("", "")).isNull();
    }

    @Test
    void relativeReferencesResolveAgainstThePageAndAbsoluteOnesAreIgnored() {
        assertThat(DocsCatalog.resolveRelative("README.md", "img.png")).isEqualTo("img.png");
        assertThat(DocsCatalog.resolveRelative("docs/api.md", "../img.png#x")).isEqualTo("img.png");
        assertThat(DocsCatalog.resolveRelative("docs/api.md", "images/a%20b.png")).isNull(); // decoded space: rejected
        assertThat(DocsCatalog.resolveRelative("docs/api.md", "%2e%2e/%2e%2e/secret.png")).isNull();
        assertThat(DocsCatalog.resolveRelative("docs/api.md", "https://example.com/x.png")).isNull();
        assertThat(DocsCatalog.resolveRelative("docs/api.md", "//example.com/x.png")).isNull();
        assertThat(DocsCatalog.resolveRelative("docs/api.md", "/img.png")).isNull();
        assertThat(DocsCatalog.resolveRelative("docs/api.md", "javascript:alert(1)")).isNull();
    }

    @Test
    void slugsAndTitlesAreDerivedFromPaths() {
        assertThat(DocsCatalog.slugFor("README.md")).isEqualTo("readme");
        assertThat(DocsCatalog.slugFor("docs/USERS_AND_SECURITY.md")).isEqualTo("users_and_security");
        assertThat(DocsCatalog.slugFor("docs/guides/Set Up.md")).isEqualTo("guides/set-up");
        assertThat(DocsCatalog.titleOf("```\n# fenced\n```\n# Real *title*\n", "x.md")).isEqualTo("Real title");
        assertThat(DocsCatalog.titleOf("no heading", "docs/runbook-notes.md")).isEqualTo("Runbook notes");
    }
}
