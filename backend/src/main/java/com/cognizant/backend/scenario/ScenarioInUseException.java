package com.cognizant.backend.scenario;

public class ScenarioInUseException extends RuntimeException {

    public ScenarioInUseException(String code, long results) {
        super("Scenario " + code + " has " + results + " saved result(s), so it cannot be deleted. "
                + "Turn it off instead; past results stay linked to it.");
    }
}
