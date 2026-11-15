package com.cognizant.backend.entity;

public enum RunStatus {
    RUNNING,
    COMPLETED,
    /** The run stopped before finishing (every call errored, or the backend restarted mid-run). */
    FAILED
}
