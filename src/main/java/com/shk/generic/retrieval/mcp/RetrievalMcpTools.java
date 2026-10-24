package com.shk.generic.retrieval.mcp;

import com.shk.generic.category.CategoryRegistry;
import com.shk.generic.category.web.CategoryResponse;
import com.shk.generic.retrieval.CategoryScope;
import com.shk.generic.retrieval.SearchQuery;
import com.shk.generic.retrieval.SearchResult;
import com.shk.generic.retrieval.answer.Answer;
import com.shk.generic.retrieval.answer.AnswerService;
import com.shk.generic.retrieval.answer.Question;
import com.shk.generic.retrieval.search.SearchService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** MCP face of retrieval. Same services, validation and filters as the REST API. */
@Component
public class RetrievalMcpTools {

    private final SearchService searchService;
    private final AnswerService answerService;
    private final CategoryRegistry categories;

    public RetrievalMcpTools(SearchService searchService, AnswerService answerService, CategoryRegistry categories) {
        this.searchService = searchService;
        this.answerService = answerService;
        this.categories = categories;
    }

    @McpTool(name = "search_knowledge_base",
            description = "Semantic search over the knowledge base. Returns the most relevant text passages with "
                    + "their source file, section, page, categories and a similarity score (0-1). "
                    + "Use list_categories to see valid category names.")
    public SearchResult search(
            @McpToolParam(description = "What to search for, in natural language") String query,
            @McpToolParam(description = "Only passages tagged with any of these categories; omit for all",
                    required = false) List<String> categories,
            @McpToolParam(description = "If true, match only documents whose primary category is one of "
                    + "the given categories", required = false) Boolean primaryOnly,
            @McpToolParam(description = "Number of passages to return (default 5, max 20)",
                    required = false) Integer topK) {
        return searchService.search(new SearchQuery(query, toSet(categories), scope(primaryOnly), topK, null));
    }

    @McpTool(name = "ask_knowledge_base",
            description = "Answer a question using only the knowledge base, with numbered citations to the "
                    + "source passages. Says so when the knowledge base doesn't contain the answer.")
    public Answer ask(
            @McpToolParam(description = "The question to answer") String question,
            @McpToolParam(description = "Only use sources tagged with any of these categories; omit for all",
                    required = false) List<String> categories) {
        return answerService.ask(new Question(question, toSet(categories), CategoryScope.ANY, null));
    }

    @McpTool(name = "list_categories",
            description = "List the active document categories with descriptions. Use these names to filter "
                    + "search_knowledge_base and ask_knowledge_base, or to tag ingest_document.")
    public List<CategoryResponse> listCategories() {
        return categories.active().stream().map(CategoryResponse::from).toList();
    }

    private static Set<String> toSet(List<String> values) {
        return values == null ? Set.of() : new LinkedHashSet<>(values);
    }

    private static CategoryScope scope(Boolean primaryOnly) {
        return Boolean.TRUE.equals(primaryOnly) ? CategoryScope.PRIMARY : CategoryScope.ANY;
    }
}
