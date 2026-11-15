package com.cognizant.backend.run;

/** A run request that cannot be started as given, e.g. an unknown model. Answered with 400. */
public class InvalidRunRequestException extends RuntimeException {

    public InvalidRunRequestException(String message) {
        super(message);
    }
}
