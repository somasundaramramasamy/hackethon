package com.cognizant.backend.run;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cognizant.backend.check.RuleCheck;
import com.cognizant.backend.check.VerdictPolicy;
import com.cognizant.backend.entity.Language;
import com.cognizant.backend.entity.ResultVerdict;
import com.cognizant.backend.entity.RunStatus;
import com.cognizant.backend.entity.RunType;
import com.cognizant.backend.entity.TestResult;
import com.cognizant.backend.entity.TestRun;
import com.cognizant.backend.judge.CriterionResult;
import com.cognizant.backend.judge.JudgeService;
import com.cognizant.backend.judge.JudgeVerdict;
import com.cognizant.backend.repository.ScenarioFixRepository;
import com.cognizant.backend.repository.TestResultRepository;
import com.cognizant.backend.repository.TestRunRepository;
import com.cognizant.backend.repository.TestScenarioRepository;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Database writes for runs. Each method is its own transaction, so progress is visible as it happens. */
@Service
public class RunRecorder {

    /** Share of passed answers, per language, picked at random for a human spot-check. */
    private static final String RESCORED = "Re-scored. ";
    private static final String RESCORED_GOOD_PRACTICE = "Re-scored: only good-practice criteria were missed. ";
    static final double PASS_SPOT_CHECK_SHARE = 0.10;
    /** Share of failed answers, per language, picked at random for a human spot-check. */
    static final double FAIL_SPOT_CHECK_SHARE = 0.20;

    /** A run that has recorded nothing for this long has stopped. A healthy run answers every few seconds. */
    public static final Duration STALE_AFTER = Duration.ofMinutes(15);

    private static final DateTimeFormatter CODE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private static final TypeReference<List<CriterionResult>> CRITERIA = new TypeReference<>() {
    };

    private final TestRunRepository runs;
    private final TestScenarioRepository scenarios;
    private final TestResultRepository results;
    private final JsonMapper json;
    private final Random random;
    private final ScenarioFixRepository fixes;

    @Autowired
    public RunRecorder(TestRunRepository runs, TestScenarioRepository scenarios, TestResultRepository results,
            JsonMapper json, ScenarioFixRepository fixes) {
        this(runs, scenarios, results, json, fixes, new Random());
    }

    RunRecorder(TestRunRepository runs, TestScenarioRepository scenarios, TestResultRepository results,
            JsonMapper json, ScenarioFixRepository fixes, Random random) {
        this.runs = runs;
        this.fixes = fixes;
        this.scenarios = scenarios;
        this.results = results;
        this.json = json;
        this.random = random;
    }

    @Transactional
    public TestRun createRun(RunType type, String name, String suite, String model, String judgeModel,
            int promptVersion, Long baselineRunId, int repeats, int plannedTests, String startedBy) {
        LocalDateTime now = LocalDateTime.now();
        String prefix = type == RunType.RETEST ? "RT-" : "RUN-";
        TestRun run = new TestRun(prefix + now.format(CODE_FORMAT), suite, type, model, judgeModel, promptVersion,
                baselineRunId, repeats, plannedTests, startedBy, now);
        run.rename(name);
        return runs.save(run);
    }

    /**
     * Saves an answer that was checked by the rules and (if reachable) the judge, and counts how the
     * saved fix it was given (if any) did.
     *
     * @return the saved result's id
     */
    @Transactional
    public Long saveChecked(Long runId, PlannedTest test, String model, String answer, long durationMs,
            Instant askedAt, RuleCheck.Outcome rule, JudgeVerdict judge, String judgeError) {
        TestResult r = newResult(runId, test, model, answer, durationMs, askedAt);
        r.recordRuleCheck(rule.verdict(), rule.detail());
        VerdictPolicy.Combined combined;
        if (judge != null) {
            r.recordJudgement(judge.verdict(), judge.confidence(), judge.reason(), json.writeValueAsString(judge.criteria()),
                    judge.translation());
            r.recordJudgeFix(judge.fix());
            combined = VerdictPolicy.combine(rule, judge.verdict(), judge.reason(), judge.criteria(), judge.confidence());
        } else {
            r.recordJudgement(ResultVerdict.ERROR, null, judgeError, null, null);
            combined = VerdictPolicy.combine(rule, null, null);
        }
        if (answer == null || answer.isBlank()) {
            combined = new VerdictPolicy.Combined(ResultVerdict.FAIL, "The assistant returned an empty answer.");
        }
        r.recordVerdict(combined.verdict(), combined.detail());
        if (test.guidance() != null) {
            r.recordAppliedFix(test.guidance().fixId(), test.guidance().text());
            fixes.recordUse(test.guidance().fixId(), test.guidance().revision(),
                    combined.verdict() == ResultVerdict.PASS ? 1 : 0, combined.verdict(), askedAt);
        }
        return results.save(r).getId();
    }

    /** Saves a question whose model call failed, so the run shows exactly what could not be checked. */
    @Transactional
    public void saveError(Long runId, PlannedTest test, String model, long durationMs, Instant askedAt, String error) {
        TestResult r = newResult(runId, test, model, null, durationMs, askedAt);
        r.recordVerdict(ResultVerdict.ERROR, "Model call failed: " + error);
        if (test.guidance() != null) {
            r.recordAppliedFix(test.guidance().fixId(), test.guidance().text());
        }
        results.save(r);
    }

    private TestResult newResult(Long runId, PlannedTest test, String model, String answer, long durationMs,
            Instant askedAt) {
        return new TestResult(runs.getReferenceById(runId), scenarios.getReferenceById(test.scenarioId()),
                test.language(), test.attempt(), test.question(), answer, model, durationMs, askedAt);
    }

    /** Scores the run, picks the spot-checks and closes it. */
    @Transactional
    public void finishRun(Long runId) {
        TestRun run = runs.findById(runId).orElseThrow();
        List<TestResult> all = results.findByTestRun_IdOrderByIdAsc(runId);
        long checked = all.stream().filter(r -> r.getVerdict() != ResultVerdict.ERROR).count();
        sampleSpotChecks(all);
        run.finish(checked == 0 ? RunStatus.FAILED : RunStatus.COMPLETED, score(all), LocalDateTime.now());
    }

    /**
     * Recomputes every verdict of a finished run from the judge's stored findings, after the
     * scenarios' criteria changed (e.g. a criterion moved to good practice). Nothing is asked again
     * and people's reviews are kept. The spot-checks are topped up to match the new verdicts.
     *
     * @return how many verdicts changed
     */
    @Transactional
    public int rescore(Long runId) {
        TestRun run = runs.findById(runId).orElseThrow();
        List<TestResult> all = results.findByTestRun_IdOrderByIdAsc(runId);
        int changed = 0;
        for (TestResult r : all) {
            if (r.getJudgeCriteria() == null || r.getJudgeVerdict() == null
                    || r.getJudgeVerdict() == ResultVerdict.ERROR || r.getAnswer() == null || r.getAnswer().isBlank()) {
                continue; // nothing the judge assessed
            }
            List<CriterionResult> stored = json.readValue(r.getJudgeCriteria(), CRITERIA);
            JudgeService.Reassessed again = JudgeService.reassess(stored, r.getScenario().getShouldDo(),
                    r.getJudgeVerdict(), r.getJudgeConfidence() == null ? 0 : r.getJudgeConfidence());
            r.rescoreJudgement(again.verdict(), json.writeValueAsString(again.criteria()));
            RuleCheck.Outcome rule = new RuleCheck.Outcome(r.getRuleVerdict(), r.getRuleDetail());
            VerdictPolicy.Combined combined = VerdictPolicy.combine(rule, again.verdict(), r.getJudgeReason(),
                    again.criteria(), r.getJudgeConfidence());
            String note;
            if (combined.verdict() != r.getVerdict()) {
                changed++;
                note = again.criteria().stream()
                        .anyMatch(c -> !JudgeService.isCritical(c) && Boolean.FALSE.equals(c.satisfied()))
                        ? RESCORED_GOOD_PRACTICE
                        : RESCORED;
            } else {
                // Same verdict: refresh the explanation, keeping any earlier re-score note.
                String old = r.getCheckDetail() == null ? "" : r.getCheckDetail();
                note = old.startsWith(RESCORED_GOOD_PRACTICE) ? RESCORED_GOOD_PRACTICE
                        : old.startsWith(RESCORED) ? RESCORED : "";
            }
            r.recordVerdict(combined.verdict(), note + combined.detail());
        }
        sampleSpotChecks(all);
        run.rescore(score(all), LocalDateTime.now());
        return changed;
    }

    /**
     * Tops up the spot-checks of every finished run, e.g. after the sampling rules changed. Already
     * picked or reviewed answers stay as they are.
     *
     * @return how many answers were newly picked
     */
    @Transactional
    public int topUpSpotChecks() {
        int picked = 0;
        for (TestRun run : runs.findByStatus(RunStatus.COMPLETED)) {
            picked += sampleSpotChecks(results.findByTestRun_IdOrderByIdAsc(run.getId()));
        }
        return picked;
    }

    /**
     * Picks answers for a human spot-check, per language: 20% of failures and 10% of passes, at least
     * one of each where there are any. Answers needing review are already queued. An answer already
     * picked or reviewed counts towards the share, so calling this again only tops up.
     *
     * @return how many answers were newly picked
     */
    int sampleSpotChecks(List<TestResult> all) {
        int picked = 0;
        for (Language language : Language.values()) {
            List<TestResult> inLanguage = all.stream().filter(r -> r.getLanguage() == language).toList();
            picked += topUp(inLanguage.stream().filter(r -> r.getVerdict() == ResultVerdict.FAIL).toList(),
                    FAIL_SPOT_CHECK_SHARE);
            picked += topUp(inLanguage.stream().filter(r -> r.getVerdict() == ResultVerdict.PASS).toList(),
                    PASS_SPOT_CHECK_SHARE);
        }
        return picked;
    }

    private int topUp(List<TestResult> group, double share) {
        if (group.isEmpty()) {
            return 0;
        }
        int wanted = Math.max(1, (int) Math.ceil(group.size() * share));
        long covered = group.stream().filter(r -> r.isAuditSample() || r.getHumanVerdict() != null).count();
        List<TestResult> candidates = new ArrayList<>(
                group.stream().filter(r -> !r.isAuditSample() && r.getHumanVerdict() == null).toList());
        Collections.shuffle(candidates, random);
        int pick = (int) Math.max(0, Math.min(candidates.size(), wanted - covered));
        candidates.subList(0, pick).forEach(TestResult::markAuditSample);
        return pick;
    }

    private static double score(List<TestResult> all) {
        long checked = all.stream().filter(r -> r.getVerdict() != ResultVerdict.ERROR).count();
        long passed = all.stream().filter(r -> r.getVerdict() == ResultVerdict.PASS).count();
        return checked == 0 ? 0 : Math.round(1000.0 * passed / checked) / 10.0;
    }

    /**
     * Closes runs that stopped making progress, e.g. after a crash, so a new run can start. The
     * database may be shared by several backends, so a run is only stale once it has recorded
     * nothing for {@link #STALE_AFTER}: a run another backend is still working on is left alone.
     */
    @Transactional
    public int failStaleRuns() {
        Instant now = Instant.now();
        int closed = 0;
        for (TestRun r : runs.findByStatus(RunStatus.RUNNING)) {
            Instant last = results.findLastAskedAt(r.getId());
            if (last == null) {
                last = r.getStartedAt().atZone(ZoneId.systemDefault()).toInstant();
            }
            if (isStale(last, now)) {
                r.markFailed(LocalDateTime.now());
                closed++;
            }
        }
        return closed;
    }

    static boolean isStale(Instant lastActivity, Instant now) {
        return lastActivity.isBefore(now.minus(STALE_AFTER));
    }
}
