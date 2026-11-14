package com.cognizant.backend.ai;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Lets the app start without API keys. Each provider is checked separately, so a missing
 * Claude key only disables the judge and investigator, not the Gemini assistant.
 */
@Component
public class AiAvailability {

    private final boolean gemini;
    private final boolean claude;

    public AiAvailability(Environment env) {
        // Check the key variables themselves: the Gemini client property holds a placeholder when unset.
        this.gemini = StringUtils.hasText(env.getProperty("GEMINI_API_KEY"));
        this.claude = StringUtils.hasText(env.getProperty("ANTHROPIC_API_KEY"));
    }

    public boolean isGeminiConfigured() {
        return gemini;
    }

    public boolean isClaudeConfigured() {
        return claude;
    }

    public void requireGemini() {
        if (!gemini) {
            throw new AiNotConfiguredException("GEMINI_API_KEY");
        }
    }

    public void requireClaude() {
        if (!claude) {
            throw new AiNotConfiguredException("ANTHROPIC_API_KEY");
        }
    }
}
