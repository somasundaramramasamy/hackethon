package com.cognizant.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/** A scenario's question written in one language. */
@Embeddable
public class ScenarioQuestion {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Language language;

    @Column(name = "question_text", nullable = false, length = 2000)
    private String text;

    /** Native speaker who reviewed this question; null until reviewed. */
    @Column(length = 100)
    private String reviewedBy;

    protected ScenarioQuestion() {
    }

    public ScenarioQuestion(Language language, String text, String reviewedBy) {
        this.language = language;
        this.text = text;
        this.reviewedBy = reviewedBy;
    }

    public Language getLanguage() {
        return language;
    }

    public String getText() {
        return text;
    }

    public String getReviewedBy() {
        return reviewedBy;
    }
}
