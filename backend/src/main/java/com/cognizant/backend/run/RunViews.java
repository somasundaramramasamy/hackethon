package com.cognizant.backend.run;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import com.cognizant.backend.judge.CriterionResult;

/** What the frontend receives for runs and results. Enum values are lower case, e.g. "needs_review". */
public final class RunViews {

    private RunViews() {
    }

    public record Counts(int total, int pass, int fail, int needsReview, int error) {
    }

    public record LanguageCounts(String language, String languageName, Counts counts) {
    }

    public record RunSummary(
            String code,
            String type,
            String status,
            String suite,
            String model,
            String judgeModel,
            Integer promptVersion,
            String baselineRun,
            int repeats,
            int plannedTests,
            int completedTests,
            String startedBy,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            double safetyScore,
            Counts counts,
            List<LanguageCounts> languages,
            int awaitingReview,
            int reviewed) {
    }

    public record ResultBrief(Long id, int attempt, String verdict, String humanVerdict, String effectiveVerdict,
            boolean auditSample) {
    }

    public record Cell(String language, String languageName, List<ResultBrief> results) {
    }

    public record ScenarioRow(String code, String title, String category, String kind, List<Cell> cells) {
    }

    public record RunDetail(RunSummary run, List<ScenarioRow> scenarios) {
    }

    public record ScenarioInfo(String code, String title, String kind, String expected, List<String> mustDo,
            List<String> mustNotDo, String sourceName, String sourceUrl) {
    }

    public record Check(String verdict, String detail) {
    }

    public record Judgement(String verdict, Double confidence, String reason, List<CriterionResult> criteria) {
    }

    public record Review(String verdict, String reviewedBy, Instant reviewedAt, String note) {
    }

    public record Sibling(Long id, String language, String languageName, String answer, String translation,
            String verdict) {
    }

    public record InvestigationBrief(Long id, String status, String conclusion, Instant startedAt) {
    }

    public record ResultDetail(
            Long id,
            String runCode,
            String runType,
            Integer promptVersion,
            ScenarioInfo scenario,
            String language,
            String languageName,
            int attempt,
            String question,
            String answer,
            String translation,
            String model,
            String judgeModel,
            long durationMs,
            Instant askedAt,
            String verdict,
            String verdictDetail,
            Check rule,
            Judgement judge,
            Review review,
            boolean auditSample,
            String effectiveVerdict,
            Sibling english,
            List<InvestigationBrief> investigations) {
    }

    /** How a scenario and language changed between a run and its retest. */
    public record CellChange(String scenarioCode, String scenarioTitle, String kind, String language,
            String languageName, List<String> before, List<String> after, String change) {
    }

    public record ChangeCounts(int fixed, int stillFailing, int newFailures, int stillPassing) {
    }

    public record Comparison(RunSummary baseline, RunSummary retest, ChangeCounts counts,
            List<CellChange> cells) {
    }
}
