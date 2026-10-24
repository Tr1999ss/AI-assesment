package com.shk.generic.retrieval.mcp;

import com.shk.generic.category.CategoryRegistry;
import com.shk.generic.category.web.CategoryResponse;
import com.shk.generic.retrieval.CategoryScope;
import com.shk.generic.retrieval.SearchQuery;
import com.shk.generic.retrieval.SearchResult;
import com.shk.generic.retrieval.answer.Answer;
import com.shk.generic.retrieval.answer.AnswerService;
import com.shk.generic.retrieval.answer.Question;
import com.shk.generic.retrieval.options.DiversifierType;
import com.shk.generic.retrieval.options.QueryTransformType;
import com.shk.generic.retrieval.options.RerankerType;
import com.shk.generic.retrieval.options.RetrievalCatalog;
import com.shk.generic.retrieval.options.RetrievalMode;
import com.shk.generic.retrieval.options.RetrievalOptions;
import com.shk.generic.retrieval.search.SearchService;
import com.shk.generic.security.Roles;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * MCP face of retrieval. Same services, validation, filters and limits as the REST API; invalid options come
 * back as tool errors naming the valid values. The MCP endpoint already requires kb-reader; the per-tool checks
 * keep each tool correct on its own. {@code list_categories} matches the public {@code GET /api/categories}.
 * <p>
 * Parameter descriptions are what the calling model reads, so each says when to use which value.
 */
@Component
public class RetrievalMcpTools {

    static final String MODE = "Retrieval method. SEMANTIC (default): meaning-based, best for natural-language "
            + "questions and paraphrases. KEYWORD: exact-term match, best for error codes, IDs, product names, "
            + "acronyms or quoted phrases. HYBRID: both, fused; best when the query mixes the two or you are unsure.";
    static final String TRANSFORM = "Rewrite the query before searching (each LLM option adds ~1-3 s). NONE (default). "
            + "REWRITE: for chatty or vague questions. MULTI_QUERY: for ambiguous questions where wording may differ "
            + "from the documents; searches several phrasings. HYDE: for short or abstract questions; searches with a "
            + "hypothetical answer.";
    static final String RERANK = "Re-score candidates for precision (adds latency). NONE (default). LLM: uses the chat "
            + "model. COHERE / CROSS_ENCODER: dedicated rerank models, only if configured (see list_retrieval_options).";
    static final String DIVERSIFY = "NONE (default). MMR: drop near-duplicate passages so the results cover more "
            + "distinct information; use for broad or overview questions.";

    private final SearchService searchService;
    private final AnswerService answerService;
    private final CategoryRegistry categories;
    private final RetrievalCatalog catalog;

    public RetrievalMcpTools(SearchService searchService, AnswerService answerService, CategoryRegistry categories,
                             RetrievalCatalog catalog) {
        this.searchService = searchService;
        this.answerService = answerService;
        this.categories = categories;
        this.catalog = catalog;
    }

    @PreAuthorize(Roles.HAS_READER)
    @McpTool(name = "search_knowledge_base",
            description = "Search the knowledge base and return the most relevant text passages with source file, "
                    + "section, page, categories and score. The response's 'applied' field says which retrieval "
                    + "settings ran and what the score means (scores of different methods are not comparable). "
                    + "Use list_categories for category names and list_retrieval_options for what is available.")
    public SearchResult search(
            @McpToolParam(description = "What to search for, in natural language or exact terms") String query,
            @McpToolParam(description = "Only passages tagged with any of these categories; omit for all",
                    required = false) List<String> categories,
            @McpToolParam(description = "If true, match only documents whose primary category is one of the given "
                    + "categories", required = false) Boolean primaryOnly,
            @McpToolParam(description = "Number of passages to return (default 5, max 20)", required = false)
            Integer topK,
            @McpToolParam(description = MODE, required = false) RetrievalMode mode,
            @McpToolParam(description = TRANSFORM, required = false) QueryTransformType queryTransform,
            @McpToolParam(description = RERANK, required = false) RerankerType rerank,
            @McpToolParam(description = DIVERSIFY, required = false) DiversifierType diversify,
            @McpToolParam(description = "Candidate pool size before reranking/MMR (between topK and the server "
                    + "limit; default 30 when pooling). Larger = better recall, slower.", required = false)
            Integer candidates,
            @McpToolParam(description = "If true, include each passage's rank and score at every stage",
                    required = false) Boolean explain) {
        RetrievalOptions options = new RetrievalOptions(mode, queryTransform, rerank, diversify, candidates, null,
                Boolean.TRUE.equals(explain));
        return searchService.search(new SearchQuery(query, toSet(categories), scope(primaryOnly), topK, null, options));
    }

    @PreAuthorize(Roles.HAS_READER)
    @McpTool(name = "ask_knowledge_base",
            description = "Answer a question using only the knowledge base, with numbered citations to the source "
                    + "passages. Says so when the knowledge base doesn't contain the answer.")
    public Answer ask(
            @McpToolParam(description = "The question to answer") String question,
            @McpToolParam(description = "Only use sources tagged with any of these categories; omit for all",
                    required = false) List<String> categories,
            @McpToolParam(description = MODE, required = false) RetrievalMode mode,
            @McpToolParam(description = TRANSFORM, required = false) QueryTransformType queryTransform,
            @McpToolParam(description = RERANK, required = false) RerankerType rerank,
            @McpToolParam(description = DIVERSIFY, required = false) DiversifierType diversify) {
        RetrievalOptions options = new RetrievalOptions(mode, queryTransform, rerank, diversify, null, null, false);
        return answerService.ask(new Question(question, toSet(categories), CategoryScope.ANY, null, options));
    }

    @PreAuthorize(Roles.HAS_READER)
    @McpTool(name = "list_retrieval_options",
            description = "List every retrieval option (mode, queryTransform, rerank, diversify), whether each is "
                    + "enabled on this server, what it is good at, its cost, plus defaults and limits.")
    public RetrievalCatalog.Catalog listRetrievalOptions() {
        return catalog.describe();
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
