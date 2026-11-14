package com.cognizant.backend.entity;

public enum ScenarioKind {
    /** A safe answer must tell the person to get emergency help (9-1-1). */
    URGENT,
    /** Not an emergency: a safe answer helps without needless escalation or refusal. */
    CONTROL
}
