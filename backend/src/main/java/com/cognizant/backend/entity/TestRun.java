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
import jakarta.persistence.Table;

@Entity
@Table(name = "test_run")
public class TestRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Public run identifier, e.g. TR-1048. */
    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @Column(nullable = false, length = 120)
    private String suite;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RunStatus status;

    @Column(nullable = false)
    private LocalDateTime startedAt;

    /** Overall safety score for the run, as a percentage (0–100). */
    @Column(nullable = false)
    private double safetyScore;

    /** SAMPLE for simulated seed data, LIVE for real calls to the assistant under test. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RunSource source = RunSource.SAMPLE;

    /** Model of the assistant under test (live runs only). */
    @Column(length = 80)
    private String model;

    private LocalDateTime finishedAt;

    @ElementCollection
    @CollectionTable(name = "test_run_language_result", joinColumns = @JoinColumn(name = "test_run_id"))
    @OrderColumn(name = "position")
    private List<LanguageResult> languageResults = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "test_run_failure", joinColumns = @JoinColumn(name = "test_run_id"))
    @OrderColumn(name = "position")
    private List<FailureCount> failures = new ArrayList<>();

    protected TestRun() {
    }

    public TestRun(String code, String suite, RunStatus status, LocalDateTime startedAt, double safetyScore) {
        this.code = code;
        this.suite = suite;
        this.status = status;
        this.startedAt = startedAt;
        this.safetyScore = safetyScore;
    }

    /** A live run that has just started: no results yet. */
    public static TestRun startLive(String code, String suite, String model, LocalDateTime startedAt) {
        TestRun run = new TestRun(code, suite, RunStatus.RUNNING, startedAt, 0);
        run.source = RunSource.LIVE;
        run.model = model;
        return run;
    }

    /** Records the final per-language counts and failure types of a live run. */
    public void finish(RunStatus finalStatus, double score, List<LanguageResult> results, List<FailureCount> failureCounts,
            LocalDateTime finishedAt) {
        this.status = finalStatus;
        this.safetyScore = score;
        this.languageResults.clear();
        this.languageResults.addAll(results);
        this.failures.clear();
        this.failures.addAll(failureCounts);
        this.finishedAt = finishedAt;
    }

    /** Marks a run that can no longer finish, e.g. after a restart. */
    public void markFailed(LocalDateTime when) {
        this.status = RunStatus.FAILED;
        this.finishedAt = when;
    }

    public void addLanguageResult(LanguageResult result) {
        languageResults.add(result);
    }

    public void addFailure(FailureCount failure) {
        failures.add(failure);
    }

    public int getTotal() {
        return languageResults.stream().mapToInt(LanguageResult::getTotal).sum();
    }

    public int getPassed() {
        return languageResults.stream().mapToInt(LanguageResult::getPassed).sum();
    }

    public int getFailed() {
        return languageResults.stream().mapToInt(LanguageResult::getFailed).sum();
    }

    public List<Language> getLanguages() {
        return languageResults.stream().map(LanguageResult::getLanguage).toList();
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

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public double getSafetyScore() {
        return safetyScore;
    }

    public RunSource getSource() {
        return source;
    }

    public String getModel() {
        return model;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public List<LanguageResult> getLanguageResults() {
        return List.copyOf(languageResults);
    }

    public List<FailureCount> getFailures() {
        return List.copyOf(failures);
    }
}
