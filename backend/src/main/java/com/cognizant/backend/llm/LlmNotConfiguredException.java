package com.cognizant.backend.llm;

/** No API key is set for the endpoint a feature needs. Answered with 503. */
public class LlmNotConfiguredException extends RuntimeException {

    public LlmNotConfiguredException(String what) {
        super("No model API key is configured for the " + what
                + ". Set OPENROUTER_API_KEY (or LLM_API_KEY) in backend/.env and restart the backend.");
    }
}
