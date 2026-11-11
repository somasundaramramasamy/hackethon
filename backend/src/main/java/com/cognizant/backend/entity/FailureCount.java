package com.cognizant.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Embeddable
public class FailureCount {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private FailureCategory category;

    @Column(name = "failure_count", nullable = false)
    private int count;

    protected FailureCount() {
    }

    public FailureCount(FailureCategory category, int count) {
        this.category = category;
        this.count = count;
    }

    public FailureCategory getCategory() {
        return category;
    }

    public int getCount() {
        return count;
    }
}
