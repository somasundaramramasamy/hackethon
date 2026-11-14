package com.cognizant.backend.ai.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.core.io.ByteArrayResource;

import com.anthropic.models.messages.OutputConfig;
import com.cognizant.backend.ai.StubChatModel;

class AiConfigTest {

    private static final AiProperties PROPS = new AiProperties("gemini-3.5-flash", "claude-opus-5-5", 4000,
            "high", "xhigh", 30);
    private static final ByteArrayResource SYSTEM = new ByteArrayResource("You are a test system prompt.".getBytes());

    @Test
    void propertiesDefaultToGeminiAssistantAndClaudeJudge() {
        AiProperties props = new AiProperties(null, null, null, null, null, null);
        assertThat(props.assistantModel()).isEqualTo("gemini-3.5-flash");
        assertThat(props.judgeModel()).isEqualTo("claude-opus-5-5");
        assertThat(props.maxTokens()).isEqualTo(16000);
        assertThat(props.judgeEffort()).isEqualTo("high");
    }

    @Test
    void geminiClientSendsModelTokensAndSystemPrompt() {
        StubChatModel model = new StubChatModel("ok", GoogleGenAiChatOptions.builder().build());
        ChatClient client = AiConfig.client(model, AiConfig.geminiOptions(PROPS), SYSTEM);

        client.prompt().user("hello").call().content();

        var options = (GoogleGenAiChatOptions) model.lastPrompt().getOptions();
        assertThat(options.getModel()).isEqualTo("gemini-3.5-flash");
        assertThat(options.getMaxOutputTokens()).isEqualTo(4000);
        assertThat(model.lastPrompt().getInstructions()).first().isInstanceOf(SystemMessage.class)
                .extracting(m -> m.getText()).isEqualTo("You are a test system prompt.");
    }

    @Test
    void claudeClientSendsModelAndEffortButNoTemperature() {
        StubChatModel model = new StubChatModel("ok", AnthropicChatOptions.builder().build());
        ChatClient client = AiConfig.client(model, AiConfig.claudeOptions(PROPS, PROPS.agentEffort()), SYSTEM);

        client.prompt().user("hello").call().content();

        var options = (AnthropicChatOptions) model.lastPrompt().getOptions();
        assertThat(options.getModel()).isEqualTo("claude-opus-5-5");
        assertThat(options.getOutputConfig().effort()).contains(OutputConfig.Effort.XHIGH);
        // Current Claude models reject sampling parameters, so none may be sent.
        assertThat(options.getTemperature()).isNull();
    }
}
