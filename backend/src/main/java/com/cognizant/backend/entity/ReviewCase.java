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

/**
 * One test scenario run across languages, with the agent's investigation and the judge's
 * decision, waiting for (or having received) a human review.
 */
@Entity
@Table(name = "review_case")
public class ReviewCase {

    /** Pipeline: 1 Test execution, 2 Investigation, 3 Evaluation, 4 Human review, 5 Retest. */
    public static final int STEP_HUMAN_REVIEW = 4;
    public static final int STEP_RETEST = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Public identifier, e.g. TR-2026-0142. */
    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 80)
    private String category;

    @Column(nullable = false, length = 80)
    private String model;

    @Column(nullable = false)
    private LocalDateTime startedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RunStatus runStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReviewStatus reviewStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Verification verification;

    @Column(nullable = false)
    private int pipelineStep;

    @Column(nullable = false, length = 2000)
    private String prompt;

    /** Overall safety score as a percentage (0–100). */
    @Column(nullable = false)
    private int overallScore;

    @Column(nullable = false, length = 80)
    private String judgeDecision;

    @Column(nullable = false, length = 1000)
    private String judgeReason;

    @ElementCollection
    @CollectionTable(name = "review_case_response", joinColumns = @JoinColumn(name = "review_case_id"))
    @OrderColumn(name = "position")
    private List<AssistantResponse> responses = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "review_case_score", joinColumns = @JoinColumn(name = "review_case_id"))
    @OrderColumn(name = "position")
    private List<EvaluationScore> scores = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "review_case_finding", joinColumns = @JoinColumn(name = "review_case_id"))
    @OrderColumn(name = "position")
    private List<Finding> findings = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "review_case_source", joinColumns = @JoinColumn(name = "review_case_id"))
    @OrderColumn(name = "position")
    private List<EvidenceSource> sources = new ArrayList<>();

    protected ReviewCase() {
    }

    public ReviewCase(String code, String title, String category, String model, LocalDateTime startedAt,
            String prompt, int overallScore, String judgeDecision, String judgeReason) {
        this.code = code;
        this.title = title;
        this.category = category;
        this.model = model;
        this.startedAt = startedAt;
        this.prompt = prompt;
        this.overallScore = overallScore;
        this.judgeDecision = judgeDecision;
        this.judgeReason = judgeReason;
        this.runStatus = RunStatus.COMPLETED;
        this.reviewStatus = ReviewStatus.NEEDS_REVIEW;
        this.verification = Verification.UNVERIFIED;
        this.pipelineStep = STEP_HUMAN_REVIEW;
    }

    /** Moves the case forward according to the reviewer's decision. */
    public void applyReviewAction(ReviewAction action) {
        switch (action) {
            case APPROVE -> {
                reviewStatus = ReviewStatus.APPROVED;
                verification = Verification.VERIFIED;
            }
            case REQUEST_FIX -> {
                reviewStatus = ReviewStatus.FIX_REQUESTED;
                verification = Verification.VERIFIED;
            }
            case RETEST -> {
                reviewStatus = ReviewStatus.RETEST_QUEUED;
                pipelineStep = STEP_RETEST;
            }
        }
    }

    public void addResponse(AssistantResponse response) {
        responses.add(response);
    }

    public void addScore(EvaluationScore score) {
        scores.add(score);
    }

    public void addFinding(Finding finding) {
        findings.add(finding);
    }

    public void addSource(EvidenceSource source) {
        sources.add(source);
    }

    public List<Language> getLanguages() {
        return responses.stream().map(AssistantResponse::getLanguage).toList();
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

    public String getModel() {
        return model;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public RunStatus getRunStatus() {
        return runStatus;
    }

    public ReviewStatus getReviewStatus() {
        return reviewStatus;
    }

    public Verification getVerification() {
        return verification;
    }

    public int getPipelineStep() {
        return pipelineStep;
    }

    public String getPrompt() {
        return prompt;
    }

    public int getOverallScore() {
        return overallScore;
    }

    public String getJudgeDecision() {
        return judgeDecision;
    }

    public String getJudgeReason() {
        return judgeReason;
    }

    public List<AssistantResponse> getResponses() {
        return List.copyOf(responses);
    }

    public List<EvaluationScore> getScores() {
        return List.copyOf(scores);
    }

    public List<Finding> getFindings() {
        return List.copyOf(findings);
    }

    public List<EvidenceSource> getSources() {
        return List.copyOf(sources);
    }
}
