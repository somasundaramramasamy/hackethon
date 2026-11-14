package com.cognizant.backend.ai;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

/** Fake model for tests: returns a fixed reply and records each prompt. Never calls the network. */
public class StubChatModel implements ChatModel {

    private final String reply;
    private final ChatOptions defaultOptions;
    private final List<Prompt> prompts = new ArrayList<>();

    /** @param defaultOptions the real provider's options type, so option merging behaves the same */
    public StubChatModel(String reply, ChatOptions defaultOptions) {
        this.reply = reply;
        this.defaultOptions = defaultOptions;
    }

    public StubChatModel(String reply) {
        this(reply, AnthropicChatOptions.builder().build());
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        prompts.add(prompt);
        return new ChatResponse(List.of(new Generation(new AssistantMessage(reply))));
    }

    @Override
    public ChatOptions getOptions() {
        return defaultOptions;
    }

    public Prompt lastPrompt() {
        return prompts.getLast();
    }
}
