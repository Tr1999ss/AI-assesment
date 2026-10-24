package com.shk.generic.retrieval.answer;

import com.shk.generic.retrieval.RetrievalProperties;
import com.shk.generic.retrieval.RetrievalUnavailableException;
import com.shk.generic.retrieval.SearchHit;
import com.shk.generic.retrieval.search.SearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Retrieval-augmented answering: search (same validation and filters as the search API) →
 * prompt with numbered sources → chat model → keep only the sources the answer cites.
 * With no relevant sources the model is not called at all.
 */
@Slf4j
@Service
public class RagAnswerService implements AnswerService {

    static final String NO_SOURCES_ANSWER =
            "I couldn't find anything relevant in the knowledge base to answer that.";

    private static final Pattern CITATION = Pattern.compile("\\[(\\d{1,3})]");

    private final SearchService searchService;
    private final AnswerPromptBuilder promptBuilder;
    private final ChatClient chatClient;
    private final RetrievalProperties properties;

    public RagAnswerService(SearchService searchService, AnswerPromptBuilder promptBuilder,
                            ChatClient.Builder chatClientBuilder, RetrievalProperties properties) {
        this.searchService = searchService;
        this.promptBuilder = promptBuilder;
        this.chatClient = chatClientBuilder.build();
        this.properties = properties;
    }

    @Override
    public Answer ask(Question question) {
        List<SearchHit> hits = searchService.search(question.toSearchQuery()).hits();
        if (hits.isEmpty()) {
            return new Answer(question.text(), NO_SOURCES_ANSWER, false, List.of());
        }
        AnswerPrompt prompt = promptBuilder.build(question.text(), hits);
        String text = generate(prompt);
        List<Citation> citations = citedSources(text, prompt.sources());
        log.debug("Answered with {} source(s) offered, {} cited", prompt.sources().size(), citations.size());
        return new Answer(question.text(), text, !citations.isEmpty(), citations);
    }

    private String generate(AnswerPrompt prompt) {
        try {
            String content = chatClient.prompt()
                    .system(prompt.system())
                    .user(prompt.user())
                    .options(ChatOptions.builder().temperature(properties.answerTemperature()))
                    .call()
                    .content();
            return content == null ? "" : content.strip();
        } catch (RuntimeException e) {
            throw new RetrievalUnavailableException("Answer generation failed: " + e.getMessage(), e);
        }
    }

    /** Sources referenced as [n] in the answer; numbers outside the offered range are ignored. */
    static List<Citation> citedSources(String answer, List<SearchHit> sources) {
        TreeSet<Integer> cited = new TreeSet<>();
        Matcher m = CITATION.matcher(answer);
        while (m.find()) {
            int n = Integer.parseInt(m.group(1));
            if (n >= 1 && n <= sources.size()) {
                cited.add(n);
            }
        }
        List<Citation> citations = new ArrayList<>(cited.size());
        for (int n : cited) {
            citations.add(Citation.of(n, sources.get(n - 1)));
        }
        return citations;
    }
}
