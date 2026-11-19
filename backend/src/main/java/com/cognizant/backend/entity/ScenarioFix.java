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
import jakarta.persistence.UniqueConstraint;

/**
 * Guidance learned from a failed answer, for one scenario in one language: what the AI judge said the
 * assistant should do differently. While it is switched on, later runs of that question give the assistant
 * this guidance.
 * There is at most one per scenario and language; a later failure that still happens with the fix
 * replaces its text with the judge's newer advice (a new revision).
 */
@Entity
@Table(name = "scenario_fix", uniqueConstraints = @UniqueConstraint(name = "uk_scenario_fix",
        columnNames = {"test_scenario_id", "language"}))
public class ScenarioFix {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "test_scenario_id", nullable = false)
    private TestScenario scenario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Language language;

    /** The instruction given to the assistant with the question. */
    @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
    private String fixText;

    /** Why the answer failed, as the judge or a reviewer put it. */
    @Column(columnDefinition = "MEDIUMTEXT")
    private String failureReason;

    private Long sourceResultId;

    private Long sourceRunId;

    @Column(nullable = false)
    private int revision;

    @Column(nullable = false)
    private boolean active;

    /** Answers given with this revision of the fix, and how many of them passed. */
    @Column(nullable = false)
    private int timesUsed;

    @Column(nullable = false)
    private int timesPassed;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private ResultVerdict lastOutcome;

    private Instant lastUsedAt;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    /** Who last turned it on or off, and why. */
    @Column(length = 100)
    private String changedBy;

    @Column(length = 500)
    private String changeNote;

    protected ScenarioFix() {
    }

    public ScenarioFix(TestScenario scenario, Language language, String fixText, String failureReason,
            Long sourceResultId, Long sourceRunId, Instant now) {
        this.scenario = scenario;
        this.language = language;
        this.fixText = fixText;
        this.failureReason = failureReason;
        this.sourceResultId = sourceResultId;
        this.sourceRunId = sourceRunId;
        this.revision = 1;
        // Starts switched off: given with the same question, a fix would make the next test pass for the wrong
        // reason. A person can switch it on deliberately.
        this.active = false;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** The fix did not stop a later failure: replace it with the newer advice and count afresh. */
    public void revise(String fixText, String failureReason, Long sourceResultId, Long sourceRunId, Instant now) {
        this.fixText = fixText;
        this.failureReason = failureReason;
        this.sourceResultId = sourceResultId;
        this.sourceRunId = sourceRunId;
        this.revision++;
        this.timesUsed = 0;
        this.timesPassed = 0;
        this.lastOutcome = null;
        this.lastUsedAt = null;
        this.updatedAt = now;
    }

    public void switchTo(boolean active, String person, String note, Instant now) {
        this.active = active;
        this.changedBy = person;
        this.changeNote = note;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public TestScenario getScenario() {
        return scenario;
    }

    public Language getLanguage() {
        return language;
    }

    public String getFixText() {
        return fixText;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Long getSourceResultId() {
        return sourceResultId;
    }

    public Long getSourceRunId() {
        return sourceRunId;
    }

    public int getRevision() {
        return revision;
    }

    public boolean isActive() {
        return active;
    }

    public int getTimesUsed() {
        return timesUsed;
    }

    public int getTimesPassed() {
        return timesPassed;
    }

    public ResultVerdict getLastOutcome() {
        return lastOutcome;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getChangedBy() {
        return changedBy;
    }

    public String getChangeNote() {
        return changeNote;
    }
}
