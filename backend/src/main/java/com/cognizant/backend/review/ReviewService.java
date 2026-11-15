package com.cognizant.backend.review;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.cognizant.backend.entity.ResultVerdict;
import com.cognizant.backend.entity.TestResult;
import com.cognizant.backend.exception.NotAllowedException;
import com.cognizant.backend.exception.ResourceNotFoundException;
import com.cognizant.backend.repository.TestResultRepository;
import com.cognizant.backend.review.ReviewViews.Agreement;

/** Human review: the queue of results a person should check, their decisions, and judge accuracy. */
@Service
@Transactional(readOnly = true)
public class ReviewService {

    private final TestResultRepository results;

    public ReviewService(TestResultRepository results) {
        this.results = results;
    }

    public List<ReviewViews.QueueItem> queue() {
        return results.findReviewQueue().stream()
                .map(r -> new ReviewViews.QueueItem(r.getId(), r.getTestRun().getCode(), r.getScenario().getCode(),
                        r.getScenario().getTitle(), lower(r.getScenario().getKind().name()), r.getLanguage().name(),
                        r.getLanguage().getDisplayName(), r.getAttempt(), lower(r.getVerdict().name()),
                        r.getCheckDetail(), r.getVerdict() == ResultVerdict.PASS ? "audit" : "flagged",
                        r.getAskedAt()))
                .toList();
    }

    public long queueSize() {
        return results.countReviewQueue();
    }

    @Transactional
    public void review(Long resultId, ReviewRequest request) {
        TestResult r = results.findById(resultId)
                .orElseThrow(() -> new ResourceNotFoundException("Result " + resultId + " not found"));
        if (r.getVerdict() == ResultVerdict.ERROR) {
            throw new NotAllowedException("This question was never answered, so there is nothing to review.");
        }
        ResultVerdict verdict = ResultVerdict.valueOf(request.verdict().strip().toUpperCase(Locale.ROOT));
        r.review(verdict, request.reviewer().strip(),
                StringUtils.hasText(request.note()) ? request.note().strip() : null, Instant.now());
    }

    public ReviewViews.AgreementReport agreement() {
        List<TestResult> reviewed = results.findReviewedWithJudgeVerdict();
        Map<String, List<TestResult>> byJudge = new LinkedHashMap<>();
        reviewed.forEach(r -> byJudge.computeIfAbsent(String.valueOf(r.getTestRun().getJudgeModel()),
                k -> new java.util.ArrayList<>()).add(r));
        return new ReviewViews.AgreementReport(agreement("all judges", reviewed),
                byJudge.entrySet().stream().map(e -> agreement(e.getKey(), e.getValue())).toList());
    }

    static Agreement agreement(String judge, List<TestResult> reviewed) {
        int agreed = 0;
        int missed = 0;
        int alarms = 0;
        for (TestResult r : reviewed) {
            if (r.getJudgeVerdict() == r.getHumanVerdict()) {
                agreed++;
            } else if (r.getJudgeVerdict() == ResultVerdict.PASS) {
                missed++;
            } else {
                alarms++;
            }
        }
        Double rate = reviewed.isEmpty() ? null : Math.round(1000.0 * agreed / reviewed.size()) / 10.0;
        return new Agreement(judge, reviewed.size(), agreed, rate, missed, alarms);
    }

    private static String lower(String s) {
        return s.toLowerCase(Locale.ROOT);
    }
}
