package com.cognizant.backend.ai.config;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import com.anthropic.models.messages.OutputConfig;
import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;

/**
 * One ChatClient per AI role. The assistant under test runs on Gemini; the judge and the
 * investigator run on Claude, so no model grades its own answers.
 * Requires {@code spring.ai.chat.client.enabled=false}, because two chat models are present.
 */
@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiConfig {

    public static final String HEALTH_ASSISTANT = "healthAssistantClient";
    public static final String JUDGE = "judgeClient";
    public static final String INVESTIGATOR = "investigatorClient";

    /**
     * Replaces Spring AI's default Gemini client so every call has a timeout and a bounded
     * number of retries; without this a stalled Gemini call can block a request indefinitely.
     */
    @Bean
    Client googleGenAiClient(@Value("${spring.ai.google.genai.api-key}") String apiKey, AiProperties props) {
        return Client.builder()
                .apiKey(apiKey)
                .httpOptions(HttpOptions.builder()
                        .timeout(props.timeoutSeconds() * 1000)
                        .retryOptions(HttpRetryOptions.builder().attempts(2).build())
                        .build())
                .build();
    }

    @Bean(HEALTH_ASSISTANT)
    ChatClient healthAssistantClient(GoogleGenAiChatModel gemini, AiProperties props,
            @Value("classpath:/prompts/health-assistant-system.st") Resource systemPrompt) {
        return client(gemini, geminiOptions(props), systemPrompt);
    }

    @Bean(JUDGE)
    ChatClient judgeClient(AnthropicChatModel claude, AiProperties props,
            @Value("classpath:/prompts/judge-system.st") Resource systemPrompt) {
        return client(claude, claudeOptions(props, props.judgeEffort()), systemPrompt);
    }

    @Bean(INVESTIGATOR)
    ChatClient investigatorClient(AnthropicChatModel claude, AiProperties props,
            @Value("classpath:/prompts/investigator-system.st") Resource systemPrompt) {
        return client(claude, claudeOptions(props, props.agentEffort()), systemPrompt);
    }

    static ChatOptions.Builder<?> geminiOptions(AiProperties props) {
        return GoogleGenAiChatOptions.builder()
                .model(props.assistantModel())
                .maxOutputTokens(props.maxTokens());
    }

    /** Temperature is never set: current Claude models reject sampling parameters. */
    static ChatOptions.Builder<?> claudeOptions(AiProperties props, String effort) {
        return AnthropicChatOptions.builder()
                .model(props.judgeModel())
                .maxTokens(props.maxTokens())
                .outputConfig(OutputConfig.builder().effort(AiProperties.toEffort(effort)).build());
    }

    static ChatClient client(ChatModel model, ChatOptions.Builder<?> options, Resource systemPrompt) {
        return ChatClient.builder(model)
                .defaultSystem(systemPrompt)
                .defaultOptions(options)
                .build();
    }
}
