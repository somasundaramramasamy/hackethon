package com.cognizant.backend.run;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

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
import com.cognizant.backend.repository.VerdictTally;
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
        List<TestRun> all = runs.findAllByOrderByStartedAtDesc();
        if (all.isEmpty()) {
            return List.of();
        }
        // One grouped count for every run, instead of loading every answer of every run.
        Map<Long, List<VerdictTally>> tallies = new HashMap<>();
        results.tallyByRun(all.stream().map(TestRun::getId).toList())
                .forEach(t -> tallies.computeIfAbsent(t.runId(), k -> new ArrayList<>()).add(t));
        Map<Long, String> codes = new HashMap<>();
        all.forEach(r -> codes.put(r.getId(), r.getCode()));
        return all.stream()
                .map(r -> summarize(r, tallies.getOrDefault(r.getId(), List.of()), codes::get))
                .toList();
    }

    public RunViews.RunDetail detail(String code) {
        TestRun run = find(code);
        List<TestResult> all = results.findByTestRun_IdOrderByIdAsc(run.getId());
        return new RunViews.RunDetail(summary(run, all), rows(all));
    }

    public RunSummary summary(String code) {
        TestRun run = find(code);
        return summarize(run, results.tallyByRun(List.of(run.getId())), this::codeOf);
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
                r.getId(), run.getCode(), run.getDisplayName(), lower(run.getType()), run.getPromptVersion(),
                new RunViews.ScenarioInfo(s.getCode(), s.getTitle(), lower(s.getKind()), s.getExpectedBehaviour(),
                        s.getMustDo(), s.getMustNotDo(), s.getShouldDo(), s.getSourceName(), s.getSourceUrl()),
                r.getLanguage().name(), r.getLanguage().getDisplayName(), r.getAttempt(), r.getQuestion(), r.getAnswer(),
                r.getTranslation(), r.getModel(), run.getJudgeModel(), r.getDurationMs(), r.getAskedAt(),
                lower(r.getVerdict()), r.getCheckDetail(),
                r.getRuleVerdict() == null ? null : new RunViews.Check(lower(r.getRuleVerdict()), r.getRuleDetail()),
                r.getJudgeVerdict() == null ? null
                        : new RunViews.Judgement(lower(r.getJudgeVerdict()), r.getJudgeConfidence(), r.getJudgeReason(),
                                criteria(r.getJudgeCriteria()), r.getJudgeFix()),
                r.getHumanVerdict() == null ? null
                        : new RunViews.Review(lower(r.getHumanVerdict()), r.getReviewedBy(), r.getReviewedAt(),
                                r.getReviewNote()),
                r.isAuditSample(), lower(r.getEffectiveVerdict()), english, invs,
                r.getAppliedFixText() == null ? null : new RunViews.AppliedFix(r.getAppliedFixId(), r.getAppliedFixText()));
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
        return summarize(run, all.stream().map(VerdictTally::of).toList(), this::codeOf);
    }

    private RunSummary summarize(TestRun run, List<VerdictTally> tallies, Function<Long, String> codeOf) {
        Map<Language, List<VerdictTally>> byLanguage = new EnumMap<>(Language.class);
        tallies.forEach(t -> byLanguage.computeIfAbsent(t.language(), k -> new ArrayList<>()).add(t));
        List<LanguageCounts> languages = byLanguage.entrySet().stream()
                .map(e -> new LanguageCounts(e.getKey().name(), e.getKey().getDisplayName(), tally(e.getValue())))
                .toList();
        String baselineCode = run.getBaselineRunId() == null ? null : codeOf.apply(run.getBaselineRunId());
        int awaiting = (int) tallies.stream().filter(VerdictTally::needsHumanReview).mapToLong(VerdictTally::count).sum();
        int reviewed = (int) tallies.stream().filter(t -> t.humanVerdict() != null).mapToLong(VerdictTally::count).sum();
        Counts counts = tally(tallies);
        return new RunSummary(run.getCode(), run.getDisplayName(), lower(run.getType()), lower(run.getStatus()), run.getSuite(),
                run.getModel(), run.getJudgeModel(), run.getPromptVersion(), baselineCode, run.getRepeats(),
                run.getPlannedTests(), counts.total(), run.getStartedBy(), run.getStartedAt(), run.getFinishedAt(),
                run.getRescoredAt(), run.getSafetyScore(), counts, languages, awaiting, reviewed);
    }

    private String codeOf(Long runId) {
        return runs.findById(runId).map(TestRun::getCode).orElse(null);
    }

    private static Counts tally(List<VerdictTally> tallies) {
        int pass = 0;
        int fail = 0;
        int review = 0;
        int error = 0;
        for (VerdictTally t : tallies) {
            int n = (int) t.count();
            switch (t.effectiveVerdict()) {
                case PASS -> pass += n;
                case FAIL -> fail += n;
                case NEEDS_REVIEW -> review += n;
                case ERROR -> error += n;
            }
        }
        return new Counts(pass + fail + review + error, pass, fail, review, error);
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
                            brief(r)));
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

    private static List<ResultBrief> verdicts(List<TestResult> list) {
        return list.stream()
                .sorted((a, b) -> Integer.compare(a.getAttempt(), b.getAttempt()))
                .map(RunQueryService::brief)
                .toList();
    }

    private static ResultBrief brief(TestResult r) {
        return new ResultBrief(r.getId(), r.getAttempt(), lower(r.getVerdict()),
                r.getHumanVerdict() == null ? null : lower(r.getHumanVerdict()), lower(r.getEffectiveVerdict()),
                r.isAuditSample());
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
