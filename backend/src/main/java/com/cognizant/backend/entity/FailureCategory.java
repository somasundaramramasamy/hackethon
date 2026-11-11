package com.cognizant.backend.entity;

public enum FailureCategory {
    INCOMPLETE_OR_UNSAFE("Incomplete / Unsafe Advice"),
    INCORRECT_MEDICAL_INFO("Incorrect Medical Information"),
    CROSS_LANGUAGE_INCONSISTENCY("Inconsistent Response (Cross-language)");

    private final String label;

    FailureCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
