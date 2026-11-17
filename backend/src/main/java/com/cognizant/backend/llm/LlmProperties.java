package com.cognizant.backend.llm;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * Model connection settings, bound from {@code kavasam.llm.*}. Any OpenAI-compatible chat completions
 * endpoint works: OpenRouter by default, or an organization's own server (vLLM, Ollama, ...). The
 * judge can use a separate endpoint, e.g. a self-hosted model, while the assistant runs elsewhere.
 *
 * @param baseUrl           endpoint for the assistant under test, e.g. https://openrouter.ai/api/v1
 * @param apiKey            key for {@code baseUrl}
 * @param judgeBaseUrl      endpoint for the judge and the agent; defaults to {@code baseUrl}
 * @param judgeApiKey       key for {@code judgeBaseUrl}; defaults to {@code apiKey}
 * @param timeoutSeconds    how long one model call may take
 * @param assistantMaxTokens answer length cap for the assistant under test
 * @param judgeMaxTokens    response cap for the judge and the agent
 * @param concurrency       how many questions a run asks at the same time
 * @param assistantRequestsPerMinute cap on calls to the assistant's endpoint; 0 = no cap
 * @param judgeRequestsPerMinute     cap on calls to the judge's endpoint, e.g. a new account's limit; 0 = no cap
 * @param assistantModels   models offered as the assistant under test; the first is the default
 * @param judgeModels       models offered as the judge; the first is the default
 */
@ConfigurationProperties(prefix = "kavasam.llm")
public record LlmProperties(
        String baseUrl,
        String apiKey,
        String judgeBaseUrl,
        String judgeApiKey,
        Integer timeoutSeconds,
        Integer assistantMaxTokens,
        Integer judgeMaxTokens,
        Integer concurrency,
        Integer assistantRequestsPerMinute,
        Integer judgeRequestsPerMinute,
        List<ModelOption> assistantModels,
        List<ModelOption> judgeModels) {

    public LlmProperties {
        baseUrl = StringUtils.hasText(baseUrl) ? baseUrl.strip() : "https://openrouter.ai/api/v1";
        apiKey = apiKey == null ? "" : apiKey.strip();
        judgeBaseUrl = StringUtils.hasText(judgeBaseUrl) ? judgeBaseUrl.strip() : baseUrl;
        judgeApiKey = StringUtils.hasText(judgeApiKey) ? judgeApiKey.strip() : apiKey;
        timeoutSeconds = timeoutSeconds == null ? 120 : timeoutSeconds;
        assistantMaxTokens = assistantMaxTokens == null ? 900 : assistantMaxTokens;
        judgeMaxTokens = judgeMaxTokens == null ? 4000 : judgeMaxTokens;
        concurrency = concurrency == null ? 4 : Math.max(1, Math.min(concurrency, 16));
        assistantRequestsPerMinute = assistantRequestsPerMinute == null ? 0 : Math.max(0, assistantRequestsPerMinute);
        judgeRequestsPerMinute = judgeRequestsPerMinute == null ? 0 : Math.max(0, judgeRequestsPerMinute);
        assistantModels = assistantModels == null || assistantModels.isEmpty()
                ? List.of(new ModelOption("google/gemma-4-31b-it", "Gemma 4 31B (Google, open)",
                        "Open model an organization might host itself"))
                : List.copyOf(assistantModels);
        judgeModels = judgeModels == null || judgeModels.isEmpty()
                ? List.of(new ModelOption("anthropic/claude-sonnet-5.5", "Claude Sonnet 5.5 (Anthropic)",
                        "Strong multilingual judge"))
                : List.copyOf(judgeModels);
    }

    public boolean isAssistantConfigured() {
        return StringUtils.hasText(apiKey);
    }

    public boolean isJudgeConfigured() {
        return StringUtils.hasText(judgeApiKey);
    }

    public boolean offersAssistant(String model) {
        return assistantModels.stream().anyMatch(m -> m.id().equals(model));
    }

    public boolean offersJudge(String model) {
        return judgeModels.stream().anyMatch(m -> m.id().equals(model));
    }
}
