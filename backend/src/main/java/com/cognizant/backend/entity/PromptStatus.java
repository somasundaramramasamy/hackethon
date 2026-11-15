package com.cognizant.backend.entity;

/** Lifecycle of a system prompt version. Only a person moves a version out of PROPOSED. */
public enum PromptStatus {
    /** The version the assistant uses for baseline runs. Exactly one at a time. */
    ACTIVE,
    /** Proposed by the agent, waiting for a person to approve or reject it. */
    PROPOSED,
    /** Approved for a retest; a person can adopt it after seeing the results. */
    APPROVED,
    REJECTED,
    /** Was active before another version was adopted; can be adopted again to roll back. */
    RETIRED
}
