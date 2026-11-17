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
 * One question asked in one language during a run: the exact question and answer, the rule check,
 * the judge's verdict with its evidence, the combined verdict, and the human review if there is one.
 */
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

    /** 1 for the first time this question was asked in the run, 2 and 3 for repeats. */
    @Column(nullable = false)
    private int attempt;

    @Column(name = "question_text", nullable = false, length = 2000)
    private String question;

    @Column(name = "answer_text", length = MAX_ANSWER_LENGTH)
    private String answer;

    @Column(nullable = false, length = 120)
    private String model;

    /** Combined verdict of the rule check and the judge (or ERROR if the model call failed). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ResultVerdict verdict;

    /** One-line explanation of the combined verdict. */
    @Column(nullable = false, length = 500)
    private String checkDetail;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private ResultVerdict ruleVerdict;

    @Column(length = 500)
    private String ruleDetail;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private ResultVerdict judgeVerdict;

    private Double judgeConfidence;

    @Column(columnDefinition = "MEDIUMTEXT")
    private String judgeReason;

    /** The judge's per-criterion results with quoted evidence, as JSON. */
    @Column(columnDefinition = "MEDIUMTEXT")
    private String judgeCriteria;

    /** English translation of a non-English answer, made by the judge. */
    @Column(columnDefinition = "MEDIUMTEXT")
    private String translation;

    @Column(nullable = false)
    private long durationMs;

    @Column(nullable = false)
    private Instant askedAt;

    /** A passed result picked at random for a human spot-check. */
    @Column(nullable = false)
    private boolean auditSample;

    /** The reviewer's verdict: PASS (safe) or FAIL (unsafe). Null until reviewed. */
    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private ResultVerdict humanVerdict;

    @Column(length = 100)
    private String reviewedBy;

    private Instant reviewedAt;

    @Column(columnDefinition = "MEDIUMTEXT")
    private String reviewNote;

    protected TestResult() {
    }

    public TestResult(TestRun testRun, TestScenario scenario, Language language, int attempt, String question,
            String answer, String model, long durationMs, Instant askedAt) {
        this.testRun = testRun;
        this.scenario = scenario;
        this.language = language;
        this.attempt = attempt;
        this.question = question;
        this.answer = answer != null && answer.length() > MAX_ANSWER_LENGTH
                ? answer.substring(0, MAX_ANSWER_LENGTH - 20) + " …[truncated]"
                : answer;
        this.model = model;
        this.durationMs = durationMs;
        this.askedAt = askedAt;
        this.verdict = ResultVerdict.ERROR;
        this.checkDetail = "Not checked.";
    }

    public void recordRuleCheck(ResultVerdict verdict, String detail) {
        this.ruleVerdict = verdict;
        this.ruleDetail = truncate(detail, 500);
    }

    public void recordJudgement(ResultVerdict verdict, Double confidence, String reason, String criteriaJson,
            String translation) {
        this.judgeVerdict = verdict;
        this.judgeConfidence = confidence;
        this.judgeReason = reason;
        this.judgeCriteria = criteriaJson;
        this.translation = translation;
    }

    /** Replaces the judge's verdict and criteria after a re-score; the judge's quotes and reason are kept. */
    public void rescoreJudgement(ResultVerdict verdict, String criteriaJson) {
        this.judgeVerdict = verdict;
        this.judgeCriteria = criteriaJson;
    }

    public void recordVerdict(ResultVerdict verdict, String detail) {
        this.verdict = verdict;
        this.checkDetail = truncate(detail, 500);
    }

    public void markAuditSample() {
        this.auditSample = true;
    }

    /** Records a person's decision. A later review replaces an earlier one. */
    public void review(ResultVerdict humanVerdict, String reviewer, String note, Instant when) {
        if (humanVerdict != ResultVerdict.PASS && humanVerdict != ResultVerdict.FAIL) {
            throw new IllegalArgumentException("A reviewer decides PASS or FAIL");
        }
        this.humanVerdict = humanVerdict;
        this.reviewedBy = reviewer;
        this.reviewNote = note;
        this.reviewedAt = when;
    }

    /**
     * Waiting for a person: the automated checks could not decide (NEEDS_REVIEW), or the answer was
     * picked for a spot-check. Other failures and passes stand unless someone reviews them.
     */
    public boolean needsHumanReview() {
        return humanVerdict == null && (verdict == ResultVerdict.NEEDS_REVIEW || auditSample);
    }

    /** The verdict to report: the person's if reviewed, otherwise the combined automated verdict. */
    public ResultVerdict getEffectiveVerdict() {
        return humanVerdict != null ? humanVerdict : verdict;
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() > max ? text.substring(0, max - 3) + "..." : text;
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

    public int getAttempt() {
        return attempt;
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

    public ResultVerdict getRuleVerdict() {
        return ruleVerdict;
    }

    public String getRuleDetail() {
        return ruleDetail;
    }

    public ResultVerdict getJudgeVerdict() {
        return judgeVerdict;
    }

    public Double getJudgeConfidence() {
        return judgeConfidence;
    }

    public String getJudgeReason() {
        return judgeReason;
    }

    public String getJudgeCriteria() {
        return judgeCriteria;
    }

    public String getTranslation() {
        return translation;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public Instant getAskedAt() {
        return askedAt;
    }

    public boolean isAuditSample() {
        return auditSample;
    }

    public ResultVerdict getHumanVerdict() {
        return humanVerdict;
    }

    public String getReviewedBy() {
        return reviewedBy;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public String getReviewNote() {
        return reviewNote;
    }
}
