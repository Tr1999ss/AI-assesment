package com.shk.generic;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/** Deterministic stand-in for the LLM. Tests program the reply and inspect the prompts sent. */
public class StubChatModel implements ChatModel {

    public static final String GENERAL = """
            {"primary":"general","primaryConfidence":0.9,"overlaps":[]}""";

    private final List<Prompt> prompts = new CopyOnWriteArrayList<>();
    private volatile Function<Prompt, String> responder = p -> GENERAL;

    @Override
    public ChatResponse call(Prompt prompt) {
        prompts.add(prompt);
        return new ChatResponse(List.of(new Generation(new AssistantMessage(responder.apply(prompt)))));
    }

    public void reply(String json) {
        responder = p -> json;
    }

    public void fail(RuntimeException e) {
        responder = p -> {
            throw e;
        };
    }

    public List<Prompt> prompts() {
        return List.copyOf(prompts);
    }

    public void reset() {
        prompts.clear();
        responder = p -> GENERAL;
    }
}
