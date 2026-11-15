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

/**
 * The investigator agent's work on one result: each step it chose (with its reasoning and the
 * assistant's reply), and its conclusion. The conclusion is a recommendation for a person.
 */
@Entity
@Table(name = "investigation")
public class Investigation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "test_result_id", nullable = false)
    private TestResult result;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private InvestigationStatus status;

    @Column(nullable = false, length = 120)
    private String agentModel;

    /** CONFIRMED_FAILURE, NOT_A_FAILURE or UNCLEAR. */
    @Column(length = 24)
    private String conclusion;

    /** SAFETY, FACTUAL or LINGUISTIC. */
    @Column(length = 16)
    private String failureType;

    @Column(columnDefinition = "MEDIUMTEXT")
    private String diagnosis;

    @Column(columnDefinition = "MEDIUMTEXT")
    private String suggestedFix;

    /** The decision log, as JSON: one entry per step. */
    @Column(columnDefinition = "MEDIUMTEXT")
    private String steps;

    @Column(length = 500)
    private String error;

    @Column(length = 100)
    private String startedBy;

    @Column(nullable = false)
    private Instant startedAt;

    private Instant finishedAt;

    protected Investigation() {
    }

    public Investigation(TestResult result, String agentModel, String startedBy, Instant startedAt) {
        this.result = result;
        this.agentModel = agentModel;
        this.startedBy = startedBy;
        this.startedAt = startedAt;
        this.status = InvestigationStatus.RUNNING;
        this.steps = "[]";
    }

    public void recordSteps(String stepsJson) {
        this.steps = stepsJson;
    }

    public void complete(String conclusion, String failureType, String diagnosis, String suggestedFix,
            String stepsJson, Instant when) {
        this.status = InvestigationStatus.COMPLETED;
        this.conclusion = conclusion;
        this.failureType = failureType;
        this.diagnosis = diagnosis;
        this.suggestedFix = suggestedFix;
        this.steps = stepsJson;
        this.finishedAt = when;
    }

    public void fail(String error, String stepsJson, Instant when) {
        this.status = InvestigationStatus.FAILED;
        this.error = error == null ? "Unknown error" : error.length() > 500 ? error.substring(0, 497) + "..." : error;
        if (stepsJson != null) {
            this.steps = stepsJson;
        }
        this.finishedAt = when;
    }

    public Long getId() {
        return id;
    }

    public TestResult getResult() {
        return result;
    }

    public InvestigationStatus getStatus() {
        return status;
    }

    public String getAgentModel() {
        return agentModel;
    }

    public String getConclusion() {
        return conclusion;
    }

    public String getFailureType() {
        return failureType;
    }

    public String getDiagnosis() {
        return diagnosis;
    }

    public String getSuggestedFix() {
        return suggestedFix;
    }

    public String getSteps() {
        return steps;
    }

    public String getError() {
        return error;
    }

    public String getStartedBy() {
        return startedBy;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }
}
