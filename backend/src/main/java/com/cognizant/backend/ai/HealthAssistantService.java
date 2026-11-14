package com.cognizant.backend.ai;

import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.cognizant.backend.ai.config.AiConfig;
import com.cognizant.backend.ai.config.AiProperties;
import com.cognizant.backend.ai.dto.AssistantAnswer;
import com.cognizant.backend.ai.dto.MultilingualAnswer;
import com.cognizant.backend.entity.Language;
import com.google.genai.errors.ApiException;
import com.google.genai.errors.GenAiIOException;

/**
 * The sample health information assistant that Kavasam tests, running on Gemini. It uses one
 * system prompt for every language, so differences between languages come from the model.
 */
@Service
public class HealthAssistantService {

    private final ChatClient chatClient;
    private final AiProperties props;
    private final AiAvailability availability;

    public HealthAssistantService(@Qualifier(AiConfig.HEALTH_ASSISTANT) ChatClient chatClient,
            AiProperties props, AiAvailability availability) {
        this.chatClient = chatClient;
        this.props = props;
        this.availability = availability;
    }

    public AssistantAnswer ask(String question, Language language) {
        availability.requireGemini();
        String answer;
        try {
            answer = chatClient.prompt()
                    .user(question)
                    .call()
                    .content();
        } catch (RuntimeException e) {
            throw unwrapGeminiError(e);
        }
        return new AssistantAnswer(question, language, answer == null ? "" : answer.strip(),
                props.assistantModel(), Instant.now());
    }

    /**
     * Spring AI wraps Gemini errors in a plain RuntimeException ("Failed to generate content").
     * Rethrow the Gemini error itself so callers can tell a rate limit (429) or timeout apart
     * and retry or report it properly.
     */
    static RuntimeException unwrapGeminiError(RuntimeException e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof ApiException || t instanceof GenAiIOException) {
                return (RuntimeException) t;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return e;
    }

    /** Asks the same question in each language, one call per language, in EN, TA, HI order. */
    public MultilingualAnswer askInEachLanguage(Map<Language, String> questions) {
        availability.requireGemini();
        var answers = new TreeMap<>(questions).entrySet().stream()
                .map(e -> ask(e.getValue(), e.getKey()))
                .toList();
        return new MultilingualAnswer(props.assistantModel(), answers);
    }
}
