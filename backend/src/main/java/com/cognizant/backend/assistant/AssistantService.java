package com.cognizant.backend.assistant;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.cognizant.backend.llm.ChatMessage;
import com.cognizant.backend.llm.LlmClient;
import com.cognizant.backend.llm.LlmProperties;
import com.cognizant.backend.llm.LlmReply;

/**
 * The health assistant under test: a chosen model with one system prompt version, the same prompt for
 * every language, so any difference between languages comes from the model.
 */
@Service
public class AssistantService {

    private final LlmClient llm;
    private final LlmProperties props;

    public AssistantService(LlmClient llm, LlmProperties props) {
        this.llm = llm;
        this.props = props;
    }

    /** Asks one question in a new conversation. */
    public LlmReply ask(String model, String systemPrompt, String question) {
        return converse(model, systemPrompt, List.of(ChatMessage.user(question)));
    }

    /** Continues a conversation; {@code turns} alternate user and assistant, ending with a user turn. */
    public LlmReply converse(String model, String systemPrompt, List<ChatMessage> turns) {
        List<ChatMessage> messages = new ArrayList<>(turns.size() + 1);
        messages.add(ChatMessage.system(systemPrompt));
        messages.addAll(turns);
        return llm.chat(LlmClient.Role.ASSISTANT, model, messages, props.assistantMaxTokens());
    }
}
