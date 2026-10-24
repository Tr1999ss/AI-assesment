package com.shk.generic.retrieval;

import com.shk.generic.AbstractIntegrationTest;
import com.shk.generic.TestTokens;
import com.shk.generic.retrieval.eval.EvaluationService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every retrieval mode and stage against a real Postgres (pgvector + full-text), with the evaluation corpus
 * as fixture data. LLM stages use the stub chat model, routed by prompt content.
 */
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RetrievalModesTests extends AbstractIntegrationTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    EvaluationService evaluation;

    @BeforeAll
    void seedCorpus() {
        evaluation.seedCorpus(evaluation.loadDefault());
    }

    @Test
    void defaultIsUnchangedPlainSemanticSearch() {
        MvcTestResult r = search("q=annual leave days per year");

        assertThat(r).hasStatusOk();
        assertThat(r).bodyJson().extractingPath("$.applied.mode").isEqualTo("SEMANTIC");
        assertThat(r).bodyJson().extractingPath("$.applied.scoreType").isEqualTo("COSINE");
        assertThat(r).bodyJson().extractingPath("$.applied.candidates").isEqualTo(5);
        assertThat(r).bodyJson().extractingPath("$.applied.rrfK").isNull();
        assertThat(r).bodyJson().extractingPath("$.applied.keywordEngine").isNull();
        assertThat(r).bodyJson().extractingPath("$.applied.llmCalls").isEqualTo(0);
        assertThat(r).bodyJson().doesNotHavePath("$.explain");
        assertThat(r).bodyJson().doesNotHavePath("$.hits[0].trace");
    }

    @Test
    void keywordFindsExactCodesWithItsOwnScoreType() {
        MvcTestResult r = search("q=ERR-4012&mode=keyword");

        assertThat(r).hasStatusOk();
        assertThat(r).bodyJson().extractingPath("$.hits[0].source").isEqualTo("payments-runbook.md");
        assertThat(r).bodyJson().extractingPath("$.hits[0].title").isEqualTo("Error codes");
        assertThat(r).bodyJson().extractingPath("$.hits[0].scoreType").isEqualTo("FTS_RANK");
        assertThat(r).bodyJson().extractingPath("$.applied.keywordEngine").isEqualTo("POSTGRES_FTS");
        assertThat(r).bodyJson().extractingPath("$.applied.minScore").isNull();
    }

    @Test
    void hybridFusesBothLegsWithRrfAndExplainsEveryStage() {
        MvcTestResult r = search("q=ERR-4012 gateway timed out&mode=hybrid&explain=true");

        assertThat(r).hasStatusOk();
        assertThat(r).bodyJson().extractingPath("$.applied.scoreType").isEqualTo("RRF");
        assertThat(r).bodyJson().extractingPath("$.applied.rrfK").isEqualTo(60);
        assertThat(r).bodyJson().extractingPath("$.applied.candidates").isEqualTo(30);
        assertThat(r).bodyJson().extractingPath("$.explain.stages[*].name").asArray()
                .contains("semantic[0]", "keyword[0]", "rrf");
        assertThat(r).bodyJson().extractingPath("$.hits[0].title").isEqualTo("Error codes");
        assertThat(r).bodyJson().extractingPath("$.hits[0].trace[*].stage").asArray()
                .contains("semantic[0]", "keyword[0]", "rrf");
    }

    @Test
    void everyLegAppliesTheSameCategoryFilter() {
        for (String mode : List.of("semantic", "keyword", "hybrid")) {
            MvcTestResult legal = search("q=customer data backups retention passwords&minScore=0&topK=20&categories=legal&mode=" + mode);
            assertThat(legal).hasStatusOk();
            assertThat(legal).bodyJson().extractingPath("$.hits[*].categories").asArray()
                    .as("mode %s with categories=legal", mode)
                    .isNotEmpty()
                    .allSatisfy(cats -> assertThat(cats.toString()).contains("legal"));

            MvcTestResult primary = search("q=refunds discounts receipts plans&minScore=0&topK=20&categories=finance&scope=PRIMARY&mode=" + mode);
            assertThat(primary).bodyJson().extractingPath("$.hits[*].primaryCategory").asArray()
                    .as("mode %s with primary finance", mode)
                    .isNotEmpty()
                    .containsOnly("finance");
        }
        // receipts only exist in finance/hr documents: a lexical match must not leak through the legal filter
        assertThat(search("q=receipts&mode=keyword&categories=legal")).bodyJson().extractingPath("$.hits").asArray().isEmpty();
        assertThat(search("q=receipts&mode=hybrid&categories=legal")).bodyJson()
                .extractingPath("$.hits[*].categories").asArray()
                .allSatisfy(cats -> assertThat(cats.toString()).contains("legal"));
    }

    @Test
    void keywordMatchesAnyTermForNaturalQuestionsAndHonoursExplicitSyntax() {
        // AND-of-all-terms would match nothing here: no chunk contains every one of mani/day/get/holiday/year
        assertThat(search("q=How many days off do I get for holidays each year?&mode=keyword"))
                .bodyJson().extractingPath("$.hits").asArray().isNotEmpty();
        // quotes switch to websearch syntax: an exact phrase
        assertThat(search("q=\"parental leave\"&mode=keyword"))
                .bodyJson().extractingPath("$.hits[*].title").asArray().containsOnly("Parental leave");
        // a leading '-' excludes
        assertThat(search("q=leave -parental -annual -sick&mode=keyword&categories=hr&topK=20"))
                .bodyJson().extractingPath("$.hits[*].title").asArray()
                .doesNotContain("Parental leave", "Annual leave", "Sick leave");
    }

    @Test
    void keywordInputIsNeverParsedAsSyntax() {
        assertThat(search("q=\"unbalanced -- ERR-4012 ; DROP TABLE vector_store&mode=keyword")).hasStatusOk();
        assertThat(search("q=the and of&mode=keyword")).hasStatusOk()
                .bodyJson().extractingPath("$.hits").asArray().isEmpty();
    }

    @Test
    void invalidOrUnavailableChoicesAre400NamingTheValidOptions() {
        MvcTestResult badMode = search("q=x&mode=fuzzy");
        assertThat(badMode).hasStatus(400);
        assertThat(badMode).bodyJson().extractingPath("$.parameter").isEqualTo("mode");
        assertThat(badMode).bodyJson().extractingPath("$.validValues").asArray().containsExactly("SEMANTIC", "KEYWORD", "HYBRID");

        assertThat(search("q=x&rerank=cohere")).hasStatus(400).bodyText()
                .contains("COHERE_API_KEY is not set").contains("[NONE, LLM]");
        assertThat(search("q=x&rerank=llm&candidates=100")).hasStatus(400).bodyText().contains("LLM calls");
        assertThat(search("q=x&candidates=2")).hasStatus(400).bodyText().contains("candidates must be between");
        assertThat(search("q=x&queryTransform=telepathy")).hasStatus(400).bodyText().contains("MULTI_QUERY");
        assertThat(search("q=x&mode=Multi-Query")).hasStatus(400);
        assertThat(search("q=x&queryTransform=multi-query")).as("'-' and case are accepted").hasStatusOk();
    }

    @Test
    void modesEndpointListsEveryOptionAndRequiresReader() {
        assertThat(mvc.get().uri("/api/search/modes")).hasStatus(401);
        assertThat(mvc.get().uri("/api/search/modes").with(TestTokens.user("nobody", "offline_access"))).hasStatus(403);

        MvcTestResult r = mvc.get().uri("/api/search/modes").with(TestTokens.reader()).exchange();
        assertThat(r).hasStatusOk();
        assertThat(r).bodyJson().extractingPath("$.defaults.mode").isEqualTo("SEMANTIC");
        assertThat(r).bodyJson().extractingPath("$.limits.maxCandidates").isEqualTo(100);
        assertThat(r).bodyJson().extractingPath("$.keywordEngine").isEqualTo("POSTGRES_FTS");
        assertThat(r).bodyJson().extractingPath("$.mode[*].value").asArray().containsExactly("SEMANTIC", "KEYWORD", "HYBRID");
        assertThat(r).bodyJson().extractingPath("$.queryTransform[*].value").asArray()
                .containsExactly("NONE", "REWRITE", "MULTI_QUERY", "HYDE");
        assertThat(r).bodyJson().extractingPath("$.rerank[?(@.value=='COHERE')].enabled").asArray().containsExactly(false);
        assertThat(r).bodyJson().extractingPath("$.rerank[?(@.value=='COHERE')].unavailableReason").asArray()
                .containsExactly("COHERE_API_KEY is not set");
        assertThat(r).bodyJson().extractingPath("$.rerank[?(@.value=='LLM')].enabled").asArray().containsExactly(true);
        assertThat(r).bodyJson().extractingPath("$.diversify[*].cost").asArray().allSatisfy(c -> assertThat(c).isNotNull());
    }

    @Test
    void hydeSearchesSemanticallyWithThePassageAndLexicallyWithTheQuery() {
        chatModel.when("Write a short passage", "Employees are entitled to sixteen weeks of paid parental leave.");

        MvcTestResult r = search("q=baby time off&mode=hybrid&queryTransform=hyde&explain=true");

        assertThat(r).hasStatusOk();
        assertThat(r).bodyJson().extractingPath("$.applied.queryTransform").isEqualTo("HYDE");
        assertThat(r).bodyJson().extractingPath("$.applied.llmCalls").isEqualTo(1);
        assertThat(r).bodyJson().extractingPath("$.explain.semanticQueries").asArray()
                .containsExactly("Employees are entitled to sixteen weeks of paid parental leave.");
        assertThat(r).bodyJson().extractingPath("$.explain.keywordQueries").asArray().containsExactly("baby time off");
        assertThat(r).bodyJson().extractingPath("$.explain.stages[0].name").isEqualTo("transform:HYDE");
    }

    @Test
    void multiQuerySearchesEveryVariantAndFuses() {
        chatModel.when("different search queries", "{\"queries\":[\"parental leave\",\"maternity paternity weeks\"]}");

        MvcTestResult r = search("q=baby time off&queryTransform=multi_query&explain=true");

        assertThat(r).hasStatusOk();
        assertThat(r).bodyJson().extractingPath("$.explain.semanticQueries").asArray()
                .containsExactly("baby time off", "parental leave", "maternity paternity weeks");
        assertThat(r).bodyJson().extractingPath("$.explain.stages[*].name").asArray()
                .contains("semantic[0]", "semantic[1]", "semantic[2]", "rrf");
        assertThat(r).bodyJson().extractingPath("$.applied.scoreType").as("several lists are fused even in SEMANTIC mode")
                .isEqualTo("RRF");
    }

    @Test
    void rewriteReplacesTheQuery() {
        chatModel.when("concise search query", "parental leave weeks");

        MvcTestResult r = search("q=hey how long can new parents be off work??&mode=keyword&queryTransform=rewrite&explain=true");

        assertThat(r).bodyJson().extractingPath("$.explain.keywordQueries").asArray().containsExactly("parental leave weeks");
        assertThat(r).bodyJson().extractingPath("$.hits[0].title").isEqualTo("Parental leave");
    }

    @Test
    void llmRerankReordersByModelScores() {
        chatModel.when("judge how relevant", RetrievalModesTests::scoreParentalLeaveHighest);

        MvcTestResult r = search("q=leave&mode=keyword&categories=hr&rerank=llm&explain=true");

        assertThat(r).hasStatusOk();
        assertThat(r).bodyJson().extractingPath("$.hits[0].title").isEqualTo("Parental leave");
        assertThat(r).bodyJson().extractingPath("$.hits[0].scoreType").isEqualTo("RERANK");
        assertThat(r).bodyJson().extractingPath("$.hits[0].score").isEqualTo(1.0);
        assertThat(r).bodyJson().extractingPath("$.applied.rerank").isEqualTo("LLM");
        assertThat(r).bodyJson().extractingPath("$.hits[0].trace[*].stage").asArray().contains("keyword[0]", "rerank:LLM");
    }

    @Test
    void failingOptionalStagesDegradeWithWarningsInsteadOfFailing() {
        chatModel.when("judge how relevant", p -> {
            throw new IllegalStateException("model down, key=sk-secret");
        });
        chatModel.when("Write a short passage", p -> {
            throw new IllegalStateException("model down, key=sk-secret");
        });

        MvcTestResult r = search("q=parental leave&mode=hybrid&queryTransform=hyde&rerank=llm");

        assertThat(r).hasStatusOk();
        assertThat(r).bodyJson().extractingPath("$.applied.rerank").isEqualTo("NONE");
        assertThat(r).bodyJson().extractingPath("$.applied.queryTransform").isEqualTo("NONE");
        assertThat(r).bodyJson().extractingPath("$.warnings").asArray().hasSize(2)
                .anySatisfy(w -> assertThat(w.toString()).startsWith("rerank LLM skipped"))
                .anySatisfy(w -> assertThat(w.toString()).startsWith("queryTransform HYDE skipped"));
        assertThat(r).bodyText().doesNotContain("sk-secret");
        assertThat(r).bodyJson().extractingPath("$.hits").asArray().isNotEmpty();
    }

    @Test
    void mmrRunsOnThePoolAndLambdaOneKeepsTheOrder() {
        MvcTestResult mmr = search("q=receipts&mode=hybrid&diversify=mmr&explain=true");
        assertThat(mmr).hasStatusOk();
        assertThat(mmr).bodyJson().extractingPath("$.applied.diversify").isEqualTo("MMR");
        assertThat(mmr).bodyJson().extractingPath("$.applied.mmrLambda").isEqualTo(0.7);
        assertThat(mmr).bodyJson().extractingPath("$.explain.stages[*].name").asArray().contains("mmr");

        String plain = ids(search("q=receipts&mode=hybrid&topK=5"));
        String lambdaOne = ids(search("q=receipts&mode=hybrid&topK=5&diversify=mmr&mmrLambda=1"));
        assertThat(lambdaOne).isEqualTo(plain);
    }

    @Test
    void askHonoursOptionsReportsThemAndGuardsPerMode() throws Exception {
        chatModel.when("numbered sources", "ERR-4012 means the payment gateway timed out [1].");

        MvcTestResult r = mvc.post().uri("/api/ask").with(TestTokens.reader()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"What does ERR-4012 mean?\",\"mode\":\"keyword\"}").exchange();
        assertThat(r).hasStatusOk();
        assertThat(r).bodyJson().extractingPath("$.retrieval.mode").isEqualTo("KEYWORD");
        assertThat(r).bodyJson().extractingPath("$.citations[0].source").isEqualTo("payments-runbook.md");

        int promptsBefore = chatModel.promptsContaining("numbered sources").size();
        MvcTestResult none = mvc.post().uri("/api/ask").with(TestTokens.reader()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"zzqx vvbn\",\"mode\":\"keyword\"}").exchange();
        assertThat(none).bodyJson().extractingPath("$.grounded").isEqualTo(false);
        assertThat(none).bodyJson().extractingPath("$.answer").asString().startsWith("I couldn't find");
        assertThat(chatModel.promptsContaining("numbered sources")).as("no model call without sources").hasSize(promptsBefore);

        assertThat(mvc.post().uri("/api/ask").with(TestTokens.reader()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"x\",\"rerank\":\"nope\"}")).hasStatus(400);
    }

    private MvcTestResult search(String query) {
        return mvc.get().uri("/api/search?" + query).with(TestTokens.reader()).exchange();
    }

    private static String ids(MvcTestResult r) {
        try {
            return r.getResponse().getContentAsString().replaceAll("(?s).*?\"chunkId\":\"([^\"]+)\"|.*", "$1,");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Stub LLM reranker: 10 for the passage about parental leave ("sixteen weeks"), 1 for every other. */
    private static String scoreParentalLeaveHighest(Prompt prompt) {
        String user = prompt.getUserMessage().getText();
        Matcher m = Pattern.compile("\\[(\\d+)] ([^\\[]*)").matcher(user);
        StringBuilder json = new StringBuilder("{\"scores\":[");
        boolean first = true;
        while (m.find()) {
            int score = m.group(2).contains("sixteen weeks") ? 10 : 1;
            json.append(first ? "" : ",").append("{\"id\":").append(m.group(1)).append(",\"score\":").append(score).append("}");
            first = false;
        }
        return json.append("]}").toString();
    }
}
