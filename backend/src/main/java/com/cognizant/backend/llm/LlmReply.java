package com.cognizant.backend.llm;

/**
 * @param content      the model's text (empty if it returned none)
 * @param finishReason why it stopped, e.g. "stop" or "length" (cut off at the token limit)
 * @param outputTokens tokens generated, if the provider reports it
 */
public record LlmReply(String content, String finishReason, Integer outputTokens) {

    public boolean wasCutOff() {
        return "length".equals(finishReason);
    }
}
