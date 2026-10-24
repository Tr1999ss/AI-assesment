package com.shk.generic.retrieval.transform;

import com.shk.generic.StubChatModel;
import com.shk.generic.retrieval.RetrievalTestSupport;
import com.shk.generic.retrieval.llm.LlmCallBudget;
import com.shk.generic.retrieval.llm.LlmCallExecutor;
import com.shk.generic.retrieval.llm.LlmStageException;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QueryTransformersTest {

    private static final TransformProperties PROPS = new TransformProperties(3, 4, 60, 40);

    private final StubChatModel model = new StubChatModel();
    private final LlmCallExecutor llm = RetrievalTestSupport.llmExecutor(Duration.ofSeconds(5));
    private final LlmCallBudget budget = new LlmCallBudget(6);

    @Test
    void noopKeepsTheQuery() {
        TransformedQuery t = new NoopQueryTransformer().transform("leave days", budget);

        assertThat(t.semanticQueries()).containsExactly("leave days");
        assertThat(t.keywordQueries()).containsExactly("leave days");
        assertThat(budget.used()).isZero();
    }

    @Test
    void rewriteReplacesTheQueryForBothLegsAndStripsQuotes() {
        model.reply("\"annual leave entitlement days\"\n");

        TransformedQuery t = new RewriteQueryTransformer(ChatClient.builder(model), llm, PROPS)
                .transform("hey so how many days off do i get??", budget);

        assertThat(t.semanticQueries()).containsExactly("annual leave entitlement days");
        assertThat(t.keywordQueries()).containsExactly("annual leave entitlement days");
        assertThat(budget.used()).isEqualTo(1);
        assertThat(model.prompts().get(0).getSystemMessage().getText()).contains("Keep every specific term");
    }

    @Test
    void rewriteFallsBackToOriginalOnEmptyOutputAndTruncates() {
        model.reply("   ");
        RewriteQueryTransformer rewrite = new RewriteQueryTransformer(ChatClient.builder(model), llm, PROPS);
        assertThat(rewrite.transform("original", budget).semanticQueries()).containsExactly("original");

        model.reply("x".repeat(500));
        assertThat(rewrite.transform("q", budget).semanticQueries().get(0)).hasSize(40);
    }

    @Test
    void multiQueryKeepsOriginalFirstDedupesAndCaps() {
        model.reply("""
                {"queries":["annual leave","Annual Leave","vacation days","holiday allowance","time off","pto"]}""");

        TransformedQuery t = new MultiQueryTransformer(ChatClient.builder(model), llm, PROPS)
                .transform("days off", budget);

        // original + at most max-multi-query-variants (4); case-insensitive duplicates removed
        assertThat(t.semanticQueries()).containsExactly("days off", "annual leave", "vacation days",
                "holiday allowance", "time off");
        assertThat(t.keywordQueries()).isEqualTo(t.semanticQueries());
        assertThat(model.prompts().get(0).getSystemMessage().getText()).contains("Write 3 different search queries");
    }

    @Test
    void hydeSearchesWithPassageSemanticallyButOriginalLexically() {
        model.reply("Employees receive twenty-five days of paid annual leave per year, plus public holidays and more.");

        TransformedQuery t = new HydeQueryTransformer(ChatClient.builder(model), llm, PROPS)
                .transform("days off?", budget);

        assertThat(t.semanticQueries()).singleElement().asString()
                .startsWith("Employees receive").hasSizeLessThanOrEqualTo(60);
        assertThat(t.keywordQueries()).containsExactly("days off?");
    }

    @Test
    void modelFailureBecomesStageExceptionWithoutProviderDetails() {
        model.fail(new IllegalStateException("401 Unauthorized: key sk-secret-123"));

        assertThatThrownBy(() -> new HydeQueryTransformer(ChatClient.builder(model), llm, PROPS).transform("q", budget))
                .isInstanceOf(LlmStageException.class)
                .hasMessageContaining("transform:HYDE")
                .hasMessageNotContaining("sk-secret");
    }

    @Test
    void budgetIsEnforced() {
        model.reply("rewritten");
        LlmCallBudget one = new LlmCallBudget(1);
        RewriteQueryTransformer rewrite = new RewriteQueryTransformer(ChatClient.builder(model), llm, PROPS);
        rewrite.transform("q", one);

        assertThatThrownBy(() -> rewrite.transform("q", one))
                .isInstanceOf(LlmStageException.class)
                .hasMessageContaining("limit of 1");
    }

    @Test
    void slowModelTimesOut() {
        model.when("concise search query", p -> {
            try {
                Thread.sleep(2_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "late";
        });
        LlmCallExecutor fast = RetrievalTestSupport.llmExecutor(Duration.ofMillis(200));

        assertThatThrownBy(() -> new RewriteQueryTransformer(ChatClient.builder(model), fast, PROPS).transform("q", budget))
                .isInstanceOf(LlmStageException.class)
                .hasMessageContaining("timed out");
    }
}
