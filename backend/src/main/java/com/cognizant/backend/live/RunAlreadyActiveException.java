package com.cognizant.backend.live;

public class RunAlreadyActiveException extends RuntimeException {

    public RunAlreadyActiveException() {
        super("A live test run is already in progress. Wait for it to finish.");
    }
}
