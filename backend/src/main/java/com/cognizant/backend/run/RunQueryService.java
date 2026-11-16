package com.cognizant.backend.run;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cognizant.backend.entity.Language;
import com.cognizant.backend.entity.ResultVerdict;
import com.cognizant.backend.entity.RunType;
import com.cognizant.backend.entity.TestResult;
import com.cognizant.backend.entity.TestRun;
import com.cognizant.backend.entity.TestScenario;
import com.cognizant.backend.exception.ResourceNotFoundException;
import com.cognizant.backend.judge.CriterionResult;
import com.cognizant.backend.repository.InvestigationRepository;
import com.cognizant.backend.repository.TestResultRepository;
import com.cognizant.backend.repository.TestRunRepository;
import com.cognizant.backend.run.RunViews.CellChange;
import com.cognizant.backend.run.RunViews.ChangeCounts;
import com.cognizant.backend.run.RunViews.Counts;
import com.cognizant.backend.run.RunViews.LanguageCounts;
import com.cognizant.backend.run.RunViews.ResultBrief;
import com.cognizant.backend.run.RunViews.RunSummary;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Read-only views of runs, results and before/after comparisons. */
@Service
@Transactional(readOnly = true)
public class RunQueryService {

    private final TestRunRepository runs;
    private final TestResultRepository results;
    private final InvestigationRepository investigations;
    private final JsonMapper json;

    public RunQueryService(TestRunRepository runs, TestResultRepository results, InvestigationRepository investigations,
            JsonMapper json) {
        this.runs = runs;
        this.results = results;
        this.investigations = investigations;
        this.json = json;
    }

    public List<RunSummary> list() {
        return runs.findAllByOrderByStartedAtDesc().stream()
                .map(r -> summary(r, results.findByTestRun_IdOrderByIdAsc(r.getId())))
                .toList();
    }

    public RunViews.RunDetail detail(String code) {
        TestRun run = find(code);
        List<TestResult> all = results.findByTestRun_IdOrderByIdAsc(run.getId());
        return new RunViews.RunDetail(summary(run, all), rows(all));
    }

    public RunSummary summary(String code) {
        TestRun run = find(code);
        return summary(run, results.findByTestRun_IdOrderByIdAsc(run.getId()));
    }

    public RunViews.ResultDetail result(Long id) {
        TestResult r = results.findWithScenarioById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Result " + id + " not found"));
        TestRun run = r.getTestRun();
        TestScenario s = r.getScenario();

        RunViews.Sibling english = null;
        if (r.getLanguage() != Language.EN) {
            english = results.findByTestRun_IdOrderByIdAsc(run.getId()).stream()
                    .filter(o -> o.getScenario().getId().equals(s.getId()) && o.getLanguage() == Language.EN)
                    .filter(o -> o.getAttempt() == r.getAttempt())
                    .findFirst()
                    .map(o -> new RunViews.Sibling(o.getId(), "EN", "English", o.getAnswer(), null, lower(o.getVerdict())))
                    .orElse(null);
        }
        var invs = investigations.findByResult_IdOrderByStartedAtDesc(id).stream()
                .map(i -> new RunViews.InvestigationBrief(i.getId(), lower(i.getStatus()), i.getConclusion(),
                        i.getStartedAt()))
                .toList();
        return new RunViews.ResultDetail(
                r.getId(), run.getCode(), lower(run.getType()), run.getPromptVersion(),
                new RunViews.ScenarioInfo(s.getCode(), s.getTitle(), lower(s.getKind()), s.getExpectedBehaviour(),
                        s.getMustDo(), s.getMustNotDo(), s.getShouldDo(), s.getSourceName(), s.getSourceUrl()),
                r.getLanguage().name(), r.getLanguage().getDisplayName(), r.getAttempt(), r.getQuestion(), r.getAnswer(),
                r.getTranslation(), r.getModel(), run.getJudgeModel(), r.getDurationMs(), r.getAskedAt(),
                lower(r.getVerdict()), r.getCheckDetail(),
                r.getRuleVerdict() == null ? null : new RunViews.Check(lower(r.getRuleVerdict()), r.getRuleDetail()),
                r.getJudgeVerdict() == null ? null
                        : new RunViews.Judgement(lower(r.getJudgeVerdict()), r.getJudgeConfidence(), r.getJudgeReason(),
                                criteria(r.getJudgeCriteria())),
                r.getHumanVerdict() == null ? null
                        : new RunViews.Review(lower(r.getHumanVerdict()), r.getReviewedBy(), r.getReviewedAt(),
                                r.getReviewNote()),
                r.isAuditSample(), lower(r.getEffectiveVerdict()), english, invs);
    }

    /** Before/after for a retest: what its fix cleared, what still fails, and anything it broke. */
    public RunViews.Comparison comparison(String retestCode) {
        TestRun retest = find(retestCode);
        if (retest.getType() != RunType.RETEST || retest.getBaselineRunId() == null) {
            throw new ResourceNotFoundException("Run " + retestCode + " is not a retest");
        }
        TestRun baseline = runs.findById(retest.getBaselineRunId())
                .orElseThrow(() -> new ResourceNotFoundException("The run this retest compares against is gone"));
        List<TestResult> before = results.findByTestRun_IdOrderByIdAsc(baseline.getId());
        List<TestResult> after = results.findByTestRun_IdOrderByIdAsc(retest.getId());

        Map<String, List<TestResult>> beforeCells = cells(before);
        Map<String, List<TestResult>> afterCells = cells(after);
        int fixed = 0;
        int still = 0;
        int broken = 0;
        int passing = 0;
        List<CellChange> changes = new ArrayList<>();
        for (var e : afterCells.entrySet()) {
            List<TestResult> was = beforeCells.getOrDefault(e.getKey(), List.of());
            List<TestResult> now = e.getValue();
            boolean failedBefore = was.stream().anyMatch(r -> r.getEffectiveVerdict() != ResultVerdict.PASS);
            boolean failsNow = now.stream().anyMatch(r -> r.getEffectiveVerdict() != ResultVerdict.PASS);
            String change;
            if (failedBefore && !failsNow) {
                change = "fixed";
                fixed++;
            } else if (failedBefore) {
                change = "still_failing";
                still++;
            } else if (failsNow) {
                change = "new_failure";
                broken++;
            } else {
                change = "still_passing";
                passing++;
            }
            TestResult first = now.getFirst();
            changes.add(new CellChange(first.getScenario().getCode(), first.getScenario().getTitle(),
                    lower(first.getScenario().getKind()), first.getLanguage().name(),
                    first.getLanguage().getDisplayName(), verdicts(was), verdicts(now), change));
        }
        return new RunViews.Comparison(summary(baseline, before), summary(retest, after),
                new ChangeCounts(fixed, still, broken, passing), changes);
    }

    RunSummary summary(TestRun run, List<TestResult> all) {
        Map<Language, List<TestResult>> byLanguage = new EnumMap<>(Language.class);
        all.forEach(r -> byLanguage.computeIfAbsent(r.getLanguage(), k -> new ArrayList<>()).add(r));
        List<LanguageCounts> languages = byLanguage.entrySet().stream()
                .map(e -> new LanguageCounts(e.getKey().name(), e.getKey().getDisplayName(), counts(e.getValue())))
                .toList();
        String baselineCode = run.getBaselineRunId() == null ? null
                : runs.findById(run.getBaselineRunId()).map(TestRun::getCode).orElse(null);
        int awaiting = (int) all.stream().filter(TestResult::needsHumanReview).count();
        int reviewed = (int) all.stream().filter(r -> r.getHumanVerdict() != null).count();
        return new RunSummary(run.getCode(), lower(run.getType()), lower(run.getStatus()), run.getSuite(),
                run.getModel(), run.getJudgeModel(), run.getPromptVersion(), baselineCode, run.getRepeats(),
                run.getPlannedTests(), all.size(), run.getStartedBy(), run.getStartedAt(), run.getFinishedAt(),
                run.getRescoredAt(), run.getSafetyScore(), counts(all), languages, awaiting, reviewed);
    }

    /** Counts by the reported verdict: a person's decision overrides the automated one. */
    static Counts counts(List<TestResult> list) {
        int pass = 0;
        int fail = 0;
        int review = 0;
        int error = 0;
        for (TestResult r : list) {
            switch (r.getEffectiveVerdict()) {
                case PASS -> pass++;
                case FAIL -> fail++;
                case NEEDS_REVIEW -> review++;
                case ERROR -> error++;
            }
        }
        return new Counts(list.size(), pass, fail, review, error);
    }

    private static List<RunViews.ScenarioRow> rows(List<TestResult> all) {
        Map<Long, List<TestResult>> byScenario = new LinkedHashMap<>();
        all.stream()
                .sorted((a, b) -> Integer.compare(a.getScenario().getPosition(), b.getScenario().getPosition()))
                .forEach(r -> byScenario.computeIfAbsent(r.getScenario().getId(), k -> new ArrayList<>()).add(r));
        return byScenario.values().stream().map(list -> {
            TestScenario s = list.getFirst().getScenario();
            Map<Language, List<ResultBrief>> cells = new EnumMap<>(Language.class);
            list.stream()
                    .sorted((a, b) -> Integer.compare(a.getAttempt(), b.getAttempt()))
                    .forEach(r -> cells.computeIfAbsent(r.getLanguage(), k -> new ArrayList<>()).add(
                            new ResultBrief(r.getId(), r.getAttempt(), lower(r.getVerdict()),
                                    r.getHumanVerdict() == null ? null : lower(r.getHumanVerdict()),
                                    lower(r.getEffectiveVerdict()), r.isAuditSample())));
            return new RunViews.ScenarioRow(s.getCode(), s.getTitle(), s.getCategory(), lower(s.getKind()),
                    cells.entrySet().stream()
                            .map(e -> new RunViews.Cell(e.getKey().name(), e.getKey().getDisplayName(), e.getValue()))
                            .toList());
        }).toList();
    }

    private static Map<String, List<TestResult>> cells(List<TestResult> list) {
        Map<String, List<TestResult>> cells = new LinkedHashMap<>();
        list.stream()
                .sorted((a, b) -> {
                    int byPosition = Integer.compare(a.getScenario().getPosition(), b.getScenario().getPosition());
                    return byPosition != 0 ? byPosition : a.getLanguage().compareTo(b.getLanguage());
                })
                .forEach(r -> cells.computeIfAbsent(r.getScenario().getId() + ":" + r.getLanguage(),
                        k -> new ArrayList<>()).add(r));
        return cells;
    }

    private static List<String> verdicts(List<TestResult> list) {
        return list.stream()
                .sorted((a, b) -> Integer.compare(a.getAttempt(), b.getAttempt()))
                .map(r -> lower(r.getEffectiveVerdict()))
                .toList();
    }

    private List<CriterionResult> criteria(String stored) {
        if (stored == null || stored.isBlank()) {
            return List.of();
        }
        return json.readValue(stored, new TypeReference<List<CriterionResult>>() {
        });
    }

    private TestRun find(String code) {
        return runs.findByCode(code).orElseThrow(() -> new ResourceNotFoundException("Run " + code + " not found"));
    }

    static String lower(Enum<?> e) {
        return e == null ? null : e.name().toLowerCase(Locale.ROOT);
    }
}
