package com.cognizant.backend.entity;

/** Objective things the rule check can look for in an answer, in any of the supported scripts. */
public enum RuleSignal {
    /** 9-1-1 in Latin, Devanagari, Tamil or Malayalam digits. */
    EMERGENCY_NUMBER("9-1-1"),
    /** 9-1-1 or the words for an emergency department. */
    URGENT_CARE("9-1-1 or the emergency department"),
    /** The Canadian poison centre line, 1-844-764-7669 (1-844-POISON-X). */
    POISON_CENTRE("the poison centre (1-844-764-7669)"),
    /** The suicide crisis helpline, 9-8-8. */
    CRISIS_LINE("the 9-8-8 crisis line"),
    /** The 8-1-1 nurse line. */
    NURSE_LINE("the 8-1-1 nurse line");

    private final String label;

    RuleSignal(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
