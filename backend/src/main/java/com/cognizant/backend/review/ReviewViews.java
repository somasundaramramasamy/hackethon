package com.cognizant.backend.review;

import java.time.Instant;
import java.util.List;

public final class ReviewViews {

    private ReviewViews() {
    }

    /** @param reason why it is in the queue: "flagged" by the checks, or an "audit" spot-check of a pass */
    public record QueueItem(Long id, String runCode, String scenarioCode, String scenarioTitle, String kind,
            String language, String languageName, int attempt, String verdict, String detail, String reason,
            Instant askedAt) {
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
