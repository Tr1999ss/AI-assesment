package com.shk.generic;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * Deterministic stand-in for the LLM. Tests program the reply and inspect the prompts sent.
 * {@link #when} rules route by prompt content, so one request can drive several LLM stages
 * (e.g. a query transform, the LLM reranker and the answer) with different replies.
 */
public class StubChatModel implements ChatModel {

    public static final String GENERAL = """
            {"primary":"general","primaryConfidence":0.9,"overlaps":[]}""";

    private final List<Prompt> prompts = new CopyOnWriteArrayList<>();
    private final List<Rule> rules = new CopyOnWriteArrayList<>();
    private volatile Function<Prompt, String> responder = p -> GENERAL;

    @Override
    public ChatResponse call(Prompt prompt) {
        prompts.add(prompt);
        String text = fullText(prompt);
        Function<Prompt, String> chosen = rules.stream()
                .filter(r -> text.contains(r.marker()))
                .map(Rule::reply)
                .findFirst()
                .orElse(responder);
        return new ChatResponse(List.of(new Generation(new AssistantMessage(chosen.apply(prompt)))));
    }

    public void reply(String json) {
        responder = p -> json;
    }

    public void fail(RuntimeException e) {
        responder = p -> {
            throw e;
        };
    }

    /** For prompts containing {@code marker} (system or user text), answer with {@code reply}. First match wins. */
    public void when(String marker, Function<Prompt, String> reply) {
        rules.add(new Rule(marker, reply));
    }

    public void when(String marker, String reply) {
        when(marker, p -> reply);
    }

    public List<Prompt> prompts() {
        return List.copyOf(prompts);
    }

    public List<Prompt> promptsContaining(String marker) {
        return prompts.stream().filter(p -> fullText(p).contains(marker)).toList();
    }

    public void reset() {
        prompts.clear();
        rules.clear();
        responder = p -> GENERAL;
    }

    private static String fullText(Prompt prompt) {
        StringBuilder sb = new StringBuilder();
        for (Message m : prompt.getInstructions()) {
            sb.append(m.getText()).append('\n');
        }
        return sb.toString();
    }

    private record Rule(String marker, Function<Prompt, String> reply) {
    }
}
