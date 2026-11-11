package com.cognizant.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** One observation from the investigator agent. */
@Embeddable
public class Finding {

    @Column(nullable = false)
    private boolean passed;

    @Column(name = "finding_text", nullable = false, length = 1000)
    private String text;

    protected Finding() {
    }

    public Finding(boolean passed, String text) {
        this.passed = passed;
        this.text = text;
    }

    public boolean isPassed() {
        return passed;
    }

    public String getText() {
        return text;
    }
}
