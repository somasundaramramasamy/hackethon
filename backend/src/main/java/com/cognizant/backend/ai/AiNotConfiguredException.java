package com.cognizant.backend.ai;

public class AiNotConfiguredException extends RuntimeException {

    public AiNotConfiguredException(String envVariable) {
        super("AI is not configured: set " + envVariable + " (environment variable or backend/.env) "
                + "and restart the backend.");
    }
}
