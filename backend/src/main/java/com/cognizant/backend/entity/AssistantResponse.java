package com.cognizant.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/** The assistant's answer to the scenario prompt in one language. */
@Embeddable
public class AssistantResponse {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Language language;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Verdict verdict;

    @Column(name = "response_text", nullable = false, length = 4000)
    private String text;

    /** English translation for non-English responses; null for English. */
    @Column(length = 4000)
    private String translation;

    protected AssistantResponse() {
    }

    public AssistantResponse(Language language, Verdict verdict, String text, String translation) {
        this.language = language;
        this.verdict = verdict;
        this.text = text;
        this.translation = translation;
    }

    public Language getLanguage() {
        return language;
    }

    public Verdict getVerdict() {
        return verdict;
    }

    public String getText() {
        return text;
    }

    public String getTranslation() {
        return translation;
    }
}
