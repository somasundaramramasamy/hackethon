package com.cognizant.backend.llm;

/** A model call that failed after retries. {@code status} is the HTTP status, or 0 if none was received. */
public class LlmException extends RuntimeException {

    private final int status;

    public LlmException(int status, String message) {
        super(message);
        this.status = status;
    }

    public LlmException(int status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }

    /** Worth retrying: rate limits, provider overload and network problems. */
    public boolean isTransient() {
        return status == 0 || status == 408 || status == 429 || status >= 500;
    }
}
