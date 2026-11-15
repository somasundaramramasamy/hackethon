package com.cognizant.backend.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.json.JsonMapper;

class LlmClientTest {

    private static final String OK = """
            {"choices":[{"message":{"role":"assistant","content":"  Call 9-1-1 now.  "},"finish_reason":"stop"}],
             "usage":{"completion_tokens":6}}""";

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    private LlmClient client(String key) {
        LlmProperties props = new LlmProperties("https://models.example/v1", key, null, null, null, null, null, null,
                null, null);
        return new LlmClient(props, builder, JsonMapper.builder().build(), Duration.ZERO);
    }

    @Test
    void sendsAnOpenAiCompatibleRequestAndReadsTheReply() {
        LlmClient llm = client("secret");
        server.expect(requestTo("https://models.example/v1/chat/completions"))
                .andExpect(header("Authorization", "Bearer secret"))
                .andExpect(jsonPath("$.model").value("small-model"))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.max_tokens").value(100))
                .andRespond(withSuccess(OK, MediaType.APPLICATION_JSON));

        LlmReply reply = llm.chat(LlmClient.Role.ASSISTANT, "small-model",
                List.of(ChatMessage.system("Be safe."), ChatMessage.user("Chest pain?")), 100);

        assertThat(reply.content()).isEqualTo("Call 9-1-1 now.");
        assertThat(reply.outputTokens()).isEqualTo(6);
        assertThat(reply.wasCutOff()).isFalse();
        server.verify();
    }

    @Test
    void retriesRateLimitsThenSucceeds() {
        LlmClient llm = client("secret");
        server.expect(times(2), requestTo("https://models.example/v1/chat/completions"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .body("{\"error\":{\"message\":\"slow down\"}}").contentType(MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://models.example/v1/chat/completions"))
                .andRespond(withSuccess(OK, MediaType.APPLICATION_JSON));

        assertThat(llm.chat(LlmClient.Role.JUDGE, "judge", List.of(ChatMessage.user("x")), 10).content())
                .isEqualTo("Call 9-1-1 now.");
        server.verify();
    }

    @Test
    void doesNotRetryAClientErrorAndReportsTheProvidersMessage() {
        LlmClient llm = client("secret");
        server.expect(requestTo("https://models.example/v1/chat/completions"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .body("{\"error\":{\"message\":\"unknown model\"}}").contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> llm.chat(LlmClient.Role.ASSISTANT, "nope", List.of(ChatMessage.user("x")), 10))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("unknown model");
        server.verify();
    }

    @Test
    void treatsAnErrorInsideA200AsAFailure() {
        LlmClient llm = client("secret");
        server.expect(requestTo("https://models.example/v1/chat/completions"))
                .andRespond(withSuccess("{\"error\":{\"code\":400,\"message\":\"context too long\"}}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> llm.chat(LlmClient.Role.ASSISTANT, "m", List.of(ChatMessage.user("x")), 10))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("context too long");
    }

    @Test
    void refusesToCallWithoutAKey() {
        assertThatThrownBy(() -> client("").chat(LlmClient.Role.ASSISTANT, "m", List.of(ChatMessage.user("x")), 10))
                .isInstanceOf(LlmNotConfiguredException.class);
    }
}
