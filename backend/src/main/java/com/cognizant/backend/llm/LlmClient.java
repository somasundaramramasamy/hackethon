package com.cognizant.backend.llm;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Calls an OpenAI-compatible chat completions endpoint. Two roles use it: the assistant under test,
 * and the judge (which also runs the investigator and fix agents). Each role can point at its own
 * endpoint, so an organization can keep the judge on its own servers.
 */
@Component
public class LlmClient {

    public enum Role {
        ASSISTANT,
        JUDGE
    }

    private static final Logger log = LoggerFactory.getLogger(LlmClient.class);

    /** Attempts per call; transient failures (rate limits, overload, network) are retried. */
    static final int MAX_ATTEMPTS = 3;

    private final LlmProperties props;
    private final RestClient assistant;
    private final RestClient judge;
    private final JsonMapper json;
    private final Duration backoff;

    @Autowired
    public LlmClient(LlmProperties props, JsonMapper json) {
        this(props, RestClient.builder().requestFactory(requestFactory(props)), json, Duration.ofSeconds(5));
    }

    /** For tests: uses the builder as given (e.g. bound to a mock server) and a short backoff. */
    LlmClient(LlmProperties props, RestClient.Builder builder, JsonMapper json, Duration backoff) {
        this.props = props;
        this.json = json;
        this.backoff = backoff;
        this.assistant = client(builder, props.baseUrl(), props.apiKey());
        this.judge = client(builder, props.judgeBaseUrl(), props.judgeApiKey());
    }

    private static JdkClientHttpRequestFactory requestFactory(LlmProperties props) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(props.timeoutSeconds()));
        return factory;
    }

    private static RestClient client(RestClient.Builder builder, String baseUrl, String apiKey) {
        return builder.clone()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                // Optional attribution headers some providers (e.g. OpenRouter) use to label traffic.
                .defaultHeader("X-Title", "Kavasam")
                .build();
    }

    public boolean isConfigured(Role role) {
        return role == Role.ASSISTANT ? props.isAssistantConfigured() : props.isJudgeConfigured();
    }

    /**
     * Sends the messages and returns the reply. Retries transient failures with a growing pause.
     *
     * @throws LlmNotConfiguredException if no key is set for the role's endpoint
     * @throws LlmException              if the call still fails after retries
     */
    public LlmReply chat(Role role, String model, List<ChatMessage> messages, int maxTokens) {
        if (!isConfigured(role)) {
            throw new LlmNotConfiguredException(role == Role.ASSISTANT ? "assistant under test" : "judge");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("max_tokens", maxTokens);

        for (int attempt = 1;; attempt++) {
            try {
                return send(role == Role.ASSISTANT ? assistant : judge, body);
            } catch (LlmException e) {
                if (!e.isTransient() || attempt >= MAX_ATTEMPTS) {
                    throw e;
                }
                log.info("Model call to {} failed ({}); retrying, attempt {} of {}", model, e.getMessage(),
                        attempt + 1, MAX_ATTEMPTS);
                pause(backoff.multipliedBy(attempt));
            }
        }
    }

    private LlmReply send(RestClient client, Map<String, Object> body) {
        JsonNode response;
        try {
            response = client.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            throw new LlmException(e.getStatusCode().value(),
                    "HTTP " + e.getStatusCode().value() + ": " + errorMessage(e.getResponseBodyAsString()), e);
        } catch (ResourceAccessException e) {
            throw new LlmException(0, "Could not reach the model provider or the call timed out", e);
        }
        if (response == null) {
            throw new LlmException(502, "Empty response from the model provider");
        }
        // Some providers report errors in a 200 response.
        JsonNode error = response.path("error");
        if (!error.isMissingNode() && !error.isNull()) {
            int code = error.path("code").asInt(502);
            throw new LlmException(code, "Provider error " + code + ": " + error.path("message").asString("unknown"));
        }
        JsonNode choice = response.path("choices").path(0);
        if (choice.isMissingNode()) {
            throw new LlmException(502, "The model provider returned no answer");
        }
        String content = choice.path("message").path("content").asString("");
        String finish = choice.path("finish_reason").asString(null);
        JsonNode tokens = response.path("usage").path("completion_tokens");
        return new LlmReply(content.strip(), finish, tokens.isMissingNode() ? null : tokens.asInt(0));
    }

    private String errorMessage(String body) {
        if (body == null || body.isBlank()) {
            return "no details";
        }
        try {
            String message = json.readTree(body).path("error").path("message").asString(null);
            if (message != null) {
                return message;
            }
        } catch (RuntimeException e) {
            // Not JSON: fall through to the raw text.
        }
        return body.length() > 200 ? body.substring(0, 200) + "..." : body;
    }

    private static void pause(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException(0, "Interrupted while waiting to retry");
        }
    }
}
