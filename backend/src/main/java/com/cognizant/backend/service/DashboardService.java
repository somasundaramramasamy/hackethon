package com.cognizant.backend.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cognizant.backend.dto.DashboardResponse;
import com.cognizant.backend.dto.DashboardResponse.FailureReason;
import com.cognizant.backend.dto.DashboardResponse.Insight;
import com.cognizant.backend.dto.DashboardResponse.LanguageCoverage;
import com.cognizant.backend.dto.DashboardResponse.Part;
import com.cognizant.backend.dto.DashboardResponse.Stats;
import com.cognizant.backend.dto.DashboardResponse.TrendPoint;
import com.cognizant.backend.dto.RunSummaryResponse;
import com.cognizant.backend.entity.FailureCategory;
import com.cognizant.backend.entity.Language;
import com.cognizant.backend.entity.LanguageResult;
import com.cognizant.backend.entity.TestRun;
import com.cognizant.backend.repository.TestRunRepository;

/** Builds the dashboard from all stored test runs. */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    static final int RECENT_RUNS = 5;
    static final int TREND_POINTS = 10;
    static final int TOP_FAILURE_REASONS = 3;

    private final TestRunRepository testRunRepository;

    public DashboardService(TestRunRepository testRunRepository) {
        this.testRunRepository = testRunRepository;
    }

    public DashboardResponse getDashboard() {
        List<TestRun> runs = testRunRepository.findAllByOrderByStartedAtDesc(); // newest first

        Stats stats = buildStats(runs);
        return new DashboardResponse(
                stats,
                runs.stream().limit(RECENT_RUNS).map(RunSummaryResponse::from).toList(),
                buildTrend(runs),
                buildCoverage(runs),
                buildFailureReasons(runs),
                buildInsights(runs, stats));
    }

    private Stats buildStats(List<TestRun> runs) {
        long total = runs.stream().mapToLong(TestRun::getTotal).sum();
        long passed = runs.stream().mapToLong(TestRun::getPassed).sum();
        long failed = runs.stream().mapToLong(TestRun::getFailed).sum();
        // Change in test volume between the latest run and the one before it.
        double change = runs.size() < 2 || runs.get(1).getTotal() == 0 ? 0
                : round1(100.0 * (runs.get(0).getTotal() - runs.get(1).getTotal()) / runs.get(1).getTotal());
        return new Stats(total, change, passed, failed, total - passed - failed);
    }

    /** Oldest → newest, so the chart reads left to right. */
    private List<TrendPoint> buildTrend(List<TestRun> runs) {
        List<TrendPoint> points = new ArrayList<>(runs.stream()
                .limit(TREND_POINTS)
                .map(r -> new TrendPoint(r.getCode(), round1(r.getSafetyScore())))
                .toList());
        Collections.reverse(points);
        return points;
    }

    private List<LanguageCoverage> buildCoverage(List<TestRun> runs) {
        Map<Language, Long> counts = new EnumMap<>(Language.class);
        runs.forEach(r -> r.getLanguageResults()
                .forEach(lr -> counts.merge(lr.getLanguage(), (long) lr.getTotal(), Long::sum)));
        return counts.entrySet().stream()
                .map(e -> new LanguageCoverage(e.getKey().name(), e.getKey().getDisplayName(), e.getValue()))
                .toList();
    }

    private List<FailureReason> buildFailureReasons(List<TestRun> runs) {
        Map<FailureCategory, Long> counts = new EnumMap<>(FailureCategory.class);
        runs.forEach(r -> r.getFailures()
                .forEach(f -> counts.merge(f.getCategory(), (long) f.getCount(), Long::sum)));
        long totalFailures = counts.values().stream().mapToLong(Long::longValue).sum();
        if (totalFailures == 0) {
            return List.of();
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<FailureCategory, Long>comparingByValue().reversed())
                .limit(TOP_FAILURE_REASONS)
                .map(e -> new FailureReason(e.getKey().getLabel(), round1(100.0 * e.getValue() / totalFailures)))
                .toList();
    }

    private List<Insight> buildInsights(List<TestRun> runs, Stats stats) {
        List<Insight> insights = new ArrayList<>();
        if (runs.isEmpty()) {
            return insights;
        }

        TestRun latest = runs.get(0);
        List<Part> score = new ArrayList<>(List.of(
                Part.text("Overall safety score: "), Part.bold(pct(latest.getSafetyScore()))));
        if (runs.size() > 1) {
            double delta = round1(latest.getSafetyScore() - runs.get(1).getSafetyScore());
            String text = " (%s%s%% vs. previous run)".formatted(delta >= 0 ? "+" : "", delta);
            score.add(delta >= 0 ? Part.positive(text) : Part.text(text));
        }
        insights.add(new Insight("success", "check", score));

        if (stats.totalTests() > 0) {
            insights.add(new Insight("warning", "alert", List.of(
                    Part.bold(stats.needsReview() + " tests"),
                    Part.text(" need human review (%s)".formatted(pct(100.0 * stats.needsReview() / stats.totalTests()))))));
        }

        highestFailureSuite(runs).ifPresent(e -> insights.add(new Insight("purple", "layers", List.of(
                Part.text("Highest failures: "), Part.bold(e.getKey()), Part.text(" (" + pct(e.getValue()) + ")")))));

        languageGap(runs).ifPresent(gap -> insights.add(new Insight("info", "trend", List.of(
                Part.text("Tamil and Hindi responses show "),
                Part.bold(pct(Math.abs(gap)) + (gap >= 0 ? " lower" : " higher")),
                Part.text(" pass rate than English")))));

        return insights;
    }

    /** Suite with the highest failure rate across all its runs. */
    private Optional<Map.Entry<String, Double>> highestFailureSuite(List<TestRun> runs) {
        Map<String, long[]> bySuite = new LinkedHashMap<>(); // [failed, total]
        for (TestRun r : runs) {
            long[] acc = bySuite.computeIfAbsent(r.getSuite(), k -> new long[2]);
            acc[0] += r.getFailed();
            acc[1] += r.getTotal();
        }
        return bySuite.entrySet().stream()
                .filter(e -> e.getValue()[1] > 0)
                .map(e -> Map.entry(e.getKey(), 100.0 * e.getValue()[0] / e.getValue()[1]))
                .max(Comparator.comparingDouble(Map.Entry::getValue));
    }

    /** English pass rate minus the combined non-English pass rate, in percentage points. */
    private Optional<Double> languageGap(List<TestRun> runs) {
        long enPassed = 0, enTotal = 0, otherPassed = 0, otherTotal = 0;
        for (TestRun r : runs) {
            for (LanguageResult lr : r.getLanguageResults()) {
                if (lr.getLanguage() == Language.EN) {
                    enPassed += lr.getPassed();
                    enTotal += lr.getTotal();
                } else {
                    otherPassed += lr.getPassed();
                    otherTotal += lr.getTotal();
                }
            }
        }
        if (enTotal == 0 || otherTotal == 0) {
            return Optional.empty();
        }
        return Optional.of(100.0 * enPassed / enTotal - 100.0 * otherPassed / otherTotal);
    }

    static double round1(double value) {
        return Math.round(value * 10) / 10.0;
    }

    private static String pct(double value) {
        return round1(value) + "%";
    }
}
