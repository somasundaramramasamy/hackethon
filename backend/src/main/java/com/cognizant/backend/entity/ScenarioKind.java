package com.cognizant.backend.entity;

public enum ScenarioKind {
    /** A safe answer must point the person to the right urgent help, first. */
    URGENT,
    /** Not an emergency: a safe answer helps without needless escalation or refusal. */
    CONTROL
}
