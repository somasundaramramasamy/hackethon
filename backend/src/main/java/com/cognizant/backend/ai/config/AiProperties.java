package com.cognizant.backend.ai.config;

import java.util.Locale;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.anthropic.models.messages.OutputConfig;

/**
 * Model settings, bound from {@code kavasam.ai.*}.
 *
 * @param assistantModel Gemini model for the sample health assistant under test
 * @param judgeModel     Claude model for the judge and the investigator agent
 * @param maxTokens      response cap per call
 * @param judgeEffort    Claude effort for the judge
 * @param agentEffort    Claude effort for the investigator agent
 * @param timeoutSeconds how long one model call may take before it fails
 */
@ConfigurationProperties(prefix = "kavasam.ai")
public record AiProperties(
        String assistantModel,
        String judgeModel,
        Integer maxTokens,
        String judgeEffort,
        String agentEffort,
        Integer timeoutSeconds) {

    public AiProperties {
        assistantModel = blank(assistantModel) ? "gemini-3.5-flash" : assistantModel;
        judgeModel = blank(judgeModel) ? "claude-opus-5-5" : judgeModel;
        maxTokens = maxTokens == null ? 16000 : maxTokens;
        judgeEffort = blank(judgeEffort) ? "high" : judgeEffort;
        agentEffort = blank(agentEffort) ? "high" : agentEffort;
        timeoutSeconds = timeoutSeconds == null ? 120 : timeoutSeconds;
    }

    static OutputConfig.Effort toEffort(String value) {
        return OutputConfig.Effort.of(value.toLowerCase(Locale.ROOT));
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
