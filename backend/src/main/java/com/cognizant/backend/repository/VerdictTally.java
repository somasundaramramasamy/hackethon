package com.cognizant.backend.repository;

import com.cognizant.backend.entity.Language;
import com.cognizant.backend.entity.ResultVerdict;
import com.cognizant.backend.entity.TestResult;

/**
 * How many answers of one run share a language, verdict, human verdict and spot-check flag. Enough to
 * build a run summary without loading the answers themselves.
 */
public record VerdictTally(Long runId, Language language, ResultVerdict verdict, ResultVerdict humanVerdict,
        boolean auditSample, long count) {

    public static VerdictTally of(TestResult r) {
        return new VerdictTally(r.getTestRun().getId(), r.getLanguage(), r.getVerdict(), r.getHumanVerdict(),
                r.isAuditSample(), 1);
    }

    /** A person's decision overrides the automated one. */
    public ResultVerdict effectiveVerdict() {
        return humanVerdict != null ? humanVerdict : verdict;
    }

    /** Matches {@link TestResult#needsHumanReview()}. */
    public boolean needsHumanReview() {
        return humanVerdict == null && (verdict == ResultVerdict.NEEDS_REVIEW || auditSample);
    }
}
