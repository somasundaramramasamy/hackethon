package com.cognizant.backend.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.cognizant.backend.exception.NotAllowedException;

/**
 * One version of the assistant's system prompt. Version 1 is the starting prompt; later versions
 * are fixes proposed by the agent. A person approves or rejects each proposal, and adopts a version
 * only after its retest.
 */
@Entity
@Table(name = "assistant_prompt")
public class AssistantPrompt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private int version;

    @Column(name = "prompt_text", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String text;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PromptStatus status;

    /** What changed, in one or two sentences. */
    @Column(length = 1000)
    private String summary;

    /** Why the change should fix the failures, and what it might break. */
    @Column(columnDefinition = "MEDIUMTEXT")
    private String rationale;

    private Integer basedOnVersion;

    /** The run whose failures this version tries to fix. */
    private Long sourceRunId;

    /** "agent:&lt;model&gt;" for proposals, or a person's name. */
    @Column(nullable = false, length = 100)
    private String createdBy;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(length = 100)
    private String decidedBy;

    private Instant decidedAt;

    @Column(length = 1000)
    private String decisionNote;

    protected AssistantPrompt() {
    }

    private AssistantPrompt(int version, String text, PromptStatus status, String summary, String rationale,
            Integer basedOnVersion, Long sourceRunId, String createdBy, Instant createdAt) {
        this.version = version;
        this.text = text;
        this.status = status;
        this.summary = summary;
        this.rationale = rationale;
        this.basedOnVersion = basedOnVersion;
        this.sourceRunId = sourceRunId;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    /** The starting prompt, in use from the first run. */
    public static AssistantPrompt initial(String text, Instant now) {
        return new AssistantPrompt(1, text, PromptStatus.ACTIVE, "Starting prompt.", null, null, null, "system", now);
    }

    public static AssistantPrompt proposal(int version, String text, String summary, String rationale,
            int basedOnVersion, Long sourceRunId, String createdBy, Instant now) {
        return new AssistantPrompt(version, text, PromptStatus.PROPOSED, summary, rationale, basedOnVersion,
                sourceRunId, createdBy, now);
    }

    public void approve(String person, String note, Instant when) {
        requireStatus(PromptStatus.PROPOSED);
        decide(PromptStatus.APPROVED, person, note, when);
    }

    public void reject(String person, String note, Instant when) {
        requireStatus(PromptStatus.PROPOSED);
        decide(PromptStatus.REJECTED, person, note, when);
    }

    /** Puts this version in use. Only approved or previously used versions can be adopted. */
    public void adopt(String person, Instant when) {
        if (status != PromptStatus.APPROVED && status != PromptStatus.RETIRED) {
            throw new NotAllowedException("Only an approved or retired version can be adopted (this one is "
                    + status.name().toLowerCase() + ")");
        }
        this.status = PromptStatus.ACTIVE;
        this.decidedBy = person;
        this.decidedAt = when;
    }

    public void retire() {
        requireStatus(PromptStatus.ACTIVE);
        this.status = PromptStatus.RETIRED;
    }

    private void decide(PromptStatus next, String person, String note, Instant when) {
        this.status = next;
        this.decidedBy = person;
        this.decisionNote = note;
        this.decidedAt = when;
    }

    private void requireStatus(PromptStatus expected) {
        if (status != expected) {
            throw new NotAllowedException("Version " + version + " is " + status.name().toLowerCase()
                    + ", not " + expected.name().toLowerCase());
        }
    }

    public Long getId() {
        return id;
    }

    public int getVersion() {
        return version;
    }

    public String getText() {
        return text;
    }

    public PromptStatus getStatus() {
        return status;
    }

    public String getSummary() {
        return summary;
    }

    public String getRationale() {
        return rationale;
    }

    public Integer getBasedOnVersion() {
        return basedOnVersion;
    }

    public Long getSourceRunId() {
        return sourceRunId;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getDecidedBy() {
        return decidedBy;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public String getDecisionNote() {
        return decisionNote;
    }
}
