package com.cognizant.backend.review;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.cognizant.backend.entity.Language;
import com.cognizant.backend.entity.ResultVerdict;
import com.cognizant.backend.entity.RunStatus;
import com.cognizant.backend.entity.RunType;
import com.cognizant.backend.entity.TestResult;
import com.cognizant.backend.entity.TestRun;
import com.cognizant.backend.exception.NotAllowedException;
import com.cognizant.backend.exception.ResourceNotFoundException;
import com.cognizant.backend.repository.TestResultRepository;
import com.cognizant.backend.repository.TestRunRepository;
import com.cognizant.backend.review.ReviewViews.Agreement;
import com.cognizant.backend.run.InvalidRunRequestException;

/** Human review: the queue of results a person should check, their decisions, and judge accuracy. */
@Service
@Transactional(readOnly = true)
public class ReviewService {

    private final TestResultRepository results;
    private final TestRunRepository runs;

    public ReviewService(TestResultRepository results, TestRunRepository runs) {
        this.results = results;
        this.runs = runs;
    }

    /**
     * The queue of one run, by default the latest completed baseline, optionally for one language.
     * Needs-review answers come first, then spot-checks, in scenario order.
     */
    public ReviewViews.QueuePage queue(String runCode, String language) {
        TestRun run = StringUtils.hasText(runCode)
                ? runs.findByCode(runCode).orElseThrow(() -> new ResourceNotFoundException("Run " + runCode + " not found"))
                : defaultRun().orElse(null);
        Language filter = parseLanguage(language);
        Map<Long, Long> waiting = waitingPerRun();
        List<ReviewViews.RunOption> options = runs.findAllByOrderByStartedAtDesc().stream()
                .filter(r -> r.getStatus() != RunStatus.RUNNING)
                .map(r -> new ReviewViews.RunOption(r.getCode(), r.getDisplayName(), lower(r.getType().name()), r.getModel(),
                        r.getPromptVersion(), r.getStartedAt().atZone(ZoneId.systemDefault()).toInstant(),
                        waiting.getOrDefault(r.getId(), 0L)))
                .toList();
        if (run == null) {
            return new ReviewViews.QueuePage(null, filter == null ? null : filter.name(), options, List.of(), List.of(),
                    0, 0, 0);
        }
        List<TestResult> queued = results.findReviewQueue(run.getId()).stream()
                .sorted(Comparator.comparing((TestResult r) -> r.getVerdict() == ResultVerdict.NEEDS_REVIEW ? 0 : 1)
                        .thenComparing(r -> r.getScenario().getPosition())
                        .thenComparing(TestResult::getLanguage)
                        .thenComparing(TestResult::getAttempt))
                .toList();
        List<ReviewViews.LanguageCount> languages = Arrays.stream(Language.values())
                .map(l -> new ReviewViews.LanguageCount(l.name(), l.getDisplayName(),
                        queued.stream().filter(r -> r.getLanguage() == l).count()))
                .toList();
        List<ReviewViews.QueueItem> items = queued.stream()
                .filter(r -> filter == null || r.getLanguage() == filter)
                .map(r -> new ReviewViews.QueueItem(r.getId(), run.getCode(), r.getScenario().getCode(),
                        r.getScenario().getTitle(), lower(r.getScenario().getKind().name()), r.getLanguage().name(),
                        r.getLanguage().getDisplayName(), r.getAttempt(), lower(r.getVerdict().name()),
                        r.getCheckDetail(), r.getVerdict() == ResultVerdict.NEEDS_REVIEW ? "needs_review" : "spot_check",
                        r.getAskedAt()))
                .toList();
        long unsure = queued.stream().filter(r -> r.getVerdict() == ResultVerdict.NEEDS_REVIEW).count();
        return new ReviewViews.QueuePage(run.getCode(), filter == null ? null : filter.name(), options, languages,
                items, results.countReviewedSelection(run.getId()), unsure, queued.size() - unsure);
    }

    /**
     * What people decided in one run (default: the latest completed baseline), optionally for one
     * language or only where the person disagreed with the judge.
     */
    public List<ReviewViews.ReviewedItem> reviewed(String runCode, String language, boolean disagreementsOnly) {
        TestRun run = StringUtils.hasText(runCode)
                ? runs.findByCode(runCode).orElseThrow(() -> new ResourceNotFoundException("Run " + runCode + " not found"))
                : defaultRun().orElse(null);
        if (run == null) {
            return List.of();
        }
        Language filter = parseLanguage(language);
        return results.findReviewed(run.getId()).stream()
                .filter(r -> filter == null || r.getLanguage() == filter)
                .map(r -> new ReviewViews.ReviewedItem(r.getId(), run.getCode(), r.getScenario().getCode(),
                        r.getScenario().getTitle(), r.getLanguage().name(), r.getLanguage().getDisplayName(),
                        r.getAttempt(), lower(r.getVerdict().name()),
                        r.getJudgeVerdict() == null ? null : lower(r.getJudgeVerdict().name()),
                        lower(r.getHumanVerdict().name()), agreementOf(r.getJudgeVerdict(), r.getHumanVerdict()),
                        r.getReviewedBy(), r.getReviewNote(), r.getReviewedAt()))
                .filter(i -> !disagreementsOnly || i.agreement().equals("missed_failure")
                        || i.agreement().equals("false_alarm"))
                .toList();
    }

    /** Same rule as the agreement rate: only the judge's definite verdicts are compared. */
    static String agreementOf(ResultVerdict judge, ResultVerdict human) {
        if (judge != ResultVerdict.PASS && judge != ResultVerdict.FAIL) {
            return "not_compared";
        }
        if (judge == human) {
            return "agreed";
        }
        return judge == ResultVerdict.PASS ? "missed_failure" : "false_alarm";
    }

    /** How many answers of the default run wait for a person: the number on the navigation badge. */
    public long queueSize() {
        return defaultRun().map(r -> waitingPerRun().getOrDefault(r.getId(), 0L)).orElse(0L);
    }

    private Optional<TestRun> defaultRun() {
        return runs.findFirstByTypeAndStatusOrderByStartedAtDesc(RunType.BASELINE, RunStatus.COMPLETED);
    }

    private Map<Long, Long> waitingPerRun() {
        Map<Long, Long> counts = new HashMap<>();
        results.countReviewQueuePerRun().forEach(row -> counts.put((Long) row[0], (Long) row[1]));
        return counts;
    }

    private static Language parseLanguage(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Language.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidRunRequestException("Unknown language " + value + "; use EN, TA, HI or ML.");
        }
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
