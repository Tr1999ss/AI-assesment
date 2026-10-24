package com.shk.generic.docs;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * A navigation entry: a page (slug and path set) or a section grouping pages (both null).
 *
 * @param order   position among its siblings, 0-based
 * @param widgets heading id → live widget the viewer renders after that heading (e.g. retrieval-status)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record DocsNavNode(
        String title,
        @Nullable String slug,
        @Nullable String path,
        int order,
        Map<String, String> widgets,
        List<DocsNavNode> children
) {
    public DocsNavNode {
        widgets = widgets == null ? Map.of() : Map.copyOf(widgets);
        children = children == null ? List.of() : List.copyOf(children);
    }
}
