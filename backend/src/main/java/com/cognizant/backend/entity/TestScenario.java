package com.cognizant.backend.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/** A sincere, everyday health question asked the same way in every language. */
@Entity
@Table(name = "test_scenario")
public class TestScenario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 16)
    private String code;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 80)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ScenarioKind kind;

    /** What a safe answer does, in plain words, e.g. "Tells the person to call 9-1-1 now". */
    @Column(nullable = false, length = 500)
    private String expectedBehaviour;

    /** Display and run order. */
    @Column(nullable = false)
    private int position;

    /** Inactive scenarios are skipped by live runs but kept, because past results reference them. */
    @Column(nullable = false)
    private boolean active = true;

    /** Trusted Canadian source the criteria come from, e.g. Heart & Stroke. */
    @Column(length = 120)
    private String sourceName;

    @Column(length = 500)
    private String sourceUrl;

    private LocalDateTime updatedAt;

    @ElementCollection
    @CollectionTable(name = "test_scenario_question", joinColumns = @JoinColumn(name = "test_scenario_id"))
    @OrderColumn(name = "position")
    private List<ScenarioQuestion> questions = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "test_scenario_must_do", joinColumns = @JoinColumn(name = "test_scenario_id"))
    @OrderColumn(name = "position")
    @Column(name = "criterion_text", nullable = false, length = 300)
    private List<String> mustDo = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "test_scenario_must_not_do", joinColumns = @JoinColumn(name = "test_scenario_id"))
    @OrderColumn(name = "position")
    @Column(name = "criterion_text", nullable = false, length = 300)
    private List<String> mustNotDo = new ArrayList<>();

    protected TestScenario() {
    }

    public TestScenario(String code, String title, String category, ScenarioKind kind, String expectedBehaviour,
            int position) {
        this.code = code;
        this.title = title;
        this.category = category;
        this.kind = kind;
        this.expectedBehaviour = expectedBehaviour;
        this.position = position;
    }

    /** Adds a question; used when building scenarios in code. */
    public TestScenario ask(Language language, String text) {
        questions.add(new ScenarioQuestion(language, text, null));
        return this;
    }

    /** Replaces every editable field. Past results are unaffected: they store the question as asked. */
    public void update(String title, String category, ScenarioKind kind, String expectedBehaviour,
            List<String> mustDo, List<String> mustNotDo, String sourceName, String sourceUrl,
            List<ScenarioQuestion> questions) {
        this.title = title;
        this.category = category;
        this.kind = kind;
        this.expectedBehaviour = expectedBehaviour;
        this.sourceName = sourceName;
        this.sourceUrl = sourceUrl;
        this.mustDo.clear();
        this.mustDo.addAll(mustDo);
        this.mustNotDo.clear();
        this.mustNotDo.addAll(mustNotDo);
        this.questions.clear();
        this.questions.addAll(questions);
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getTitle() {
        return title;
    }

    public String getCategory() {
        return category;
    }

    public ScenarioKind getKind() {
        return kind;
    }

    public String getExpectedBehaviour() {
        return expectedBehaviour;
    }

    public int getPosition() {
        return position;
    }

    public boolean isActive() {
        return active;
    }

    public String getSourceName() {
        return sourceName;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public List<ScenarioQuestion> getQuestions() {
        return List.copyOf(questions);
    }

    public List<String> getMustDo() {
        return List.copyOf(mustDo);
    }

    public List<String> getMustNotDo() {
        return List.copyOf(mustNotDo);
    }
}
