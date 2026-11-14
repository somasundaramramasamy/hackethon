package com.cognizant.backend.entity;

public enum RunStatus {
    RUNNING,
    COMPLETED,
    NEEDS_REVIEW,
    /** The run stopped before finishing (every call errored, or the backend restarted mid-run). */
    FAILED
}
