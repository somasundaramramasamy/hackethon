package com.cognizant.backend.exception;

/** An action that the current state does not allow, e.g. approving a fix twice. Answered with 409. */
public class NotAllowedException extends RuntimeException {

    public NotAllowedException(String message) {
        super(message);
    }
}
