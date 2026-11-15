package com.cognizant.backend.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One test run: questions asked to the assistant under test with one system prompt version, each
 * answer checked by the rules and the judge. Results are stored in {@link TestResult}.
 */
@Entity
@Table(name = "test_run")
public class TestRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Public identifier, e.g. RUN-20261115-142233. */
    @Column(nullable = false, unique = true, length = 32)
    private String code;

    /** Short human-readable description, e.g. "Baseline: 15 scenarios × 4 languages". */
    @Column(nullable = false, length = 120)
    private String suite;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RunStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "run_type", nullable = false, length = 16)
    private RunType type;

    @Column(nullable = false)
    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    /** Share of checked answers that passed, as a percentage (0–100), set when the run finishes. */
    @Column(nullable = false)
    private double safetyScore;

    /** Model of the assistant under test. */
    @Column(length = 120)
    private String model;

    /** Model of the judge (and of the investigator agent for this run's results). */
    @Column(length = 120)
    private String judgeModel;

    /** System prompt version the assistant used. */
    private Integer promptVersion;

    /** For a retest: the run whose failures it checks again. */
    private Long baselineRunId;

    /** How many times each question is asked; repeats show whether a failure is repeatable. */
    @Column(nullable = false)
    private int repeats;

    /** Number of answers the run will collect (questions × repeats). */
    @Column(nullable = false)
    private int plannedTests;

    @Column(length = 100)
    private String startedBy;

    protected TestRun() {
    }

    public TestRun(String code, String suite, RunType type, String model, String judgeModel, int promptVersion,
            Long baselineRunId, int repeats, int plannedTests, String startedBy, LocalDateTime startedAt) {
        this.code = code;
        this.suite = suite;
        this.type = type;
        this.model = model;
        this.judgeModel = judgeModel;
        this.promptVersion = promptVersion;
        this.baselineRunId = baselineRunId;
        this.repeats = repeats;
        this.plannedTests = plannedTests;
        this.startedBy = startedBy;
        this.startedAt = startedAt;
        this.status = RunStatus.RUNNING;
    }

    public void finish(RunStatus finalStatus, double score, LocalDateTime when) {
        this.status = finalStatus;
        this.safetyScore = score;
        this.finishedAt = when;
    }

    /** Marks a run that can no longer finish, e.g. after a restart. */
    public void markFailed(LocalDateTime when) {
        this.status = RunStatus.FAILED;
        this.finishedAt = when;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getSuite() {
        return suite;
    }

    public RunStatus getStatus() {
        return status;
    }

    public RunType getType() {
        return type;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public double getSafetyScore() {
        return safetyScore;
    }

    public String getModel() {
        return model;
    }

    public String getJudgeModel() {
        return judgeModel;
    }

    public Integer getPromptVersion() {
        return promptVersion;
    }

    public Long getBaselineRunId() {
        return baselineRunId;
    }

    public int getRepeats() {
        return repeats;
    }

    public int getPlannedTests() {
        return plannedTests;
    }

    public String getStartedBy() {
        return startedBy;
    }
}
