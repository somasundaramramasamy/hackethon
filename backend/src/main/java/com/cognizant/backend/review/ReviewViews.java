package com.cognizant.backend.review;

import java.time.Instant;
import java.util.List;

public final class ReviewViews {

    private ReviewViews() {
    }

    /**
     * @param reason {@code needs_review} (the checks could not decide) or {@code spot_check} (a pass or
     *               failure picked at random so the automated verdicts are measured)
     */
    public record QueueItem(Long id, String runCode, String scenarioCode, String scenarioTitle, String kind,
            String language, String languageName, int attempt, String verdict, String detail, String reason,
            Instant askedAt) {
    }

    /** A run a reviewer can pick, with how many of its answers are waiting. */
    public record RunOption(String code, String name, String type, String model, Integer promptVersion, Instant startedAt,
            long waiting) {
    }

    public record LanguageCount(String language, String languageName, long waiting) {
    }

    /**
     * One run's queue. {@code languages}, {@code unsure} and {@code spotChecks} count every language,
     * whatever the filter, so the page can show how many wait in each.
     *
     * @param reviewed answers picked for a person in this run that a person has already reviewed
     * @param unsure   waiting because the automated checks could not decide
     * @param spotChecks waiting because they were picked at random to measure the checks
     */
    public record QueuePage(String runCode, String language, List<RunOption> runs, List<LanguageCount> languages,
            List<QueueItem> items, long reviewed, long unsure, long spotChecks) {
    }

    /**
     * How often the judge's definite verdicts (pass or fail) matched a person's.
     *
     * @param missedFailures judge said pass, person said fail: the dangerous kind of mistake
     * @param falseAlarms    judge said fail, person said pass
     */
    public record Agreement(String judgeModel, int reviewed, int agreed, Double rate, int missedFailures,
            int falseAlarms) {
    }

    public record AgreementReport(Agreement overall, List<Agreement> byJudge) {
    }
}
