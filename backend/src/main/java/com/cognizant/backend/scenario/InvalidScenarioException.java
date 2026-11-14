package com.cognizant.backend.scenario;

import java.util.Map;

/** Validation failure for scenarios that did not come through a @Valid request body (import, seeding). */
public class InvalidScenarioException extends RuntimeException {

    private final transient Map<String, String> errors;

    public InvalidScenarioException(String message, Map<String, String> errors) {
        super(message);
        this.errors = errors;
    }

    public Map<String, String> getErrors() {
        return errors;
    }
}
