package com.cognizant.backend.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** One question asked in one language during a live run, with the exact answer received. */
@Entity
@Table(name = "test_result")
public class TestResult {

    public static final int MAX_ANSWER_LENGTH = 10_000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "test_run_id", nullable = false)
    private TestRun testRun;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "test_scenario_id", nullable = false)
    private TestScenario scenario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Language language;

    @Column(name = "question_text", nullable = false, length = 2000)
    private String question;

    @Column(name = "answer_text", length = MAX_ANSWER_LENGTH)
    private String answer;

    @Column(nullable = false, length = 80)
    private String model;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ResultVerdict verdict;

    @Column(nullable = false, length = 500)
    private String checkDetail;

    @Column(nullable = false)
    private long durationMs;

    @Column(nullable = false)
    private Instant askedAt;

    protected TestResult() {
    }

    public TestResult(TestRun testRun, TestScenario scenario, Language language, String question, String answer,
            String model, ResultVerdict verdict, String checkDetail, long durationMs, Instant askedAt) {
        this.testRun = testRun;
        this.scenario = scenario;
        this.language = language;
        this.question = question;
        this.answer = answer != null && answer.length() > MAX_ANSWER_LENGTH
                ? answer.substring(0, MAX_ANSWER_LENGTH - 20) + " …[truncated]"
                : answer;
        this.model = model;
        this.verdict = verdict;
        this.checkDetail = checkDetail.length() > 500 ? checkDetail.substring(0, 497) + "..." : checkDetail;
        this.durationMs = durationMs;
        this.askedAt = askedAt;
    }

    public Long getId() {
        return id;
    }

    public TestRun getTestRun() {
        return testRun;
    }

    public TestScenario getScenario() {
        return scenario;
    }

    public Language getLanguage() {
        return language;
    }

    public String getQuestion() {
        return question;
    }

    public String getAnswer() {
        return answer;
    }

    public String getModel() {
        return model;
    }

    public ResultVerdict getVerdict() {
        return verdict;
    }

    public String getCheckDetail() {
        return checkDetail;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public Instant getAskedAt() {
        return askedAt;
    }
}
