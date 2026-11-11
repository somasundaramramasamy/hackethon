package com.cognizant.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/** Per-language outcome of one test run. Tests that neither passed nor failed are awaiting review. */
@Embeddable
public class LanguageResult {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Language language;

    @Column(name = "total_tests", nullable = false)
    private int total;

    @Column(name = "passed_tests", nullable = false)
    private int passed;

    @Column(name = "failed_tests", nullable = false)
    private int failed;

    protected LanguageResult() {
    }

    public LanguageResult(Language language, int total, int passed, int failed) {
        if (passed + failed > total) {
            throw new IllegalArgumentException("passed + failed cannot exceed total");
        }
        this.language = language;
        this.total = total;
        this.passed = passed;
        this.failed = failed;
    }

    public Language getLanguage() {
        return language;
    }

    public int getTotal() {
        return total;
    }

    public int getPassed() {
        return passed;
    }

    public int getFailed() {
        return failed;
    }
}
