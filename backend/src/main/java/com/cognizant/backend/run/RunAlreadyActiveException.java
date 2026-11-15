package com.cognizant.backend.run;

public class RunAlreadyActiveException extends RuntimeException {

    public RunAlreadyActiveException() {
        super("A test run is already in progress. Wait for it to finish before starting another.");
    }
}
