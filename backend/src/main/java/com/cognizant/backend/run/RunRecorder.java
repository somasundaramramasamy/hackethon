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
import com.cognizant.backend.entity.ResultVerdict;
import com.cognizant.backend.entity.RunStatus;
import com.cognizant.backend.entity.RunType;
import com.cognizant.backend.entity.TestResult;
import com.cognizant.backend.entity.TestRun;
import com.cognizant.backend.judge.CriterionResult;
import com.cognizant.backend.judge.JudgeService;
import com.cognizant.backend.judge.JudgeVerdict;
import com.cognizant.backend.repository.TestResultRepository;
import com.cognizant.backend.repository.TestRunRepository;
import com.cognizant.backend.repository.TestScenarioRepository;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Database writes for runs. Each method is its own transaction, so progress is visible as it happens. */
@Service
public class RunRecorder {

    /** Share of passed answers picked at random for a human spot-check. */
    static final double AUDIT_SHARE = 0.10;

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

    @Autowired
    public RunRecorder(TestRunRepository runs, TestScenarioRepository scenarios, TestResultRepository results,
            JsonMapper json) {
        this(runs, scenarios, results, json, new Random());
    }

    RunRecorder(TestRunRepository runs, TestScenarioRepository scenarios, TestResultRepository results,
            JsonMapper json, Random random) {
        this.runs = runs;
        this.scenarios = scenarios;
        this.results = results;
        this.json = json;
        this.random = random;
    }

    @Transactional
    public TestRun createRun(RunType type, String suite, String model, String judgeModel, int promptVersion,
            Long baselineRunId, int repeats, int plannedTests, String startedBy) {
        LocalDateTime now = LocalDateTime.now();
        String prefix = type == RunType.RETEST ? "RT-" : "RUN-";
        return runs.save(new TestRun(prefix + now.format(CODE_FORMAT), suite, type, model, judgeModel, promptVersion,
                baselineRunId, repeats, plannedTests, startedBy, now));
    }

    /** Saves an answer that was checked by the rules and (if reachable) the judge. */
    @Transactional
    public void saveChecked(Long runId, PlannedTest test, String model, String answer, long durationMs,
            Instant askedAt, RuleCheck.Outcome rule, JudgeVerdict judge, String judgeError) {
        TestResult r = newResult(runId, test, model, answer, durationMs, askedAt);
        r.recordRuleCheck(rule.verdict(), rule.detail());
        VerdictPolicy.Combined combined;
        if (judge != null) {
            r.recordJudgement(judge.verdict(), judge.confidence(), judge.reason(), json.writeValueAsString(judge.criteria()),
                    judge.translation());
            combined = VerdictPolicy.combine(rule, judge.verdict(), judge.reason());
        } else {
            r.recordJudgement(ResultVerdict.ERROR, null, judgeError, null, null);
            combined = VerdictPolicy.combine(rule, null, null);
        }
        if (answer == null || answer.isBlank()) {
            combined = new VerdictPolicy.Combined(ResultVerdict.FAIL, "The assistant returned an empty answer.");
        }
        r.recordVerdict(combined.verdict(), combined.detail());
        results.save(r);
    }

    /** Saves a question whose model call failed, so the run shows exactly what could not be checked. */
    @Transactional
    public void saveError(Long runId, PlannedTest test, String model, long durationMs, Instant askedAt, String error) {
        TestResult r = newResult(runId, test, model, null, durationMs, askedAt);
        r.recordVerdict(ResultVerdict.ERROR, "Model call failed: " + error);
        results.save(r);
    }

    private TestResult newResult(Long runId, PlannedTest test, String model, String answer, long durationMs,
            Instant askedAt) {
        return new TestResult(runs.getReferenceById(runId), scenarios.getReferenceById(test.scenarioId()),
                test.language(), test.attempt(), test.question(), answer, model, durationMs, askedAt);
    }

    /** Scores the run, picks the audit sample and closes it. */
    @Transactional
    public void finishRun(Long runId) {
        TestRun run = runs.findById(runId).orElseThrow();
        List<TestResult> all = results.findByTestRun_IdOrderByIdAsc(runId);
        long checked = all.stream().filter(r -> r.getVerdict() != ResultVerdict.ERROR).count();

        List<TestResult> passes = new ArrayList<>(all.stream().filter(r -> r.getVerdict() == ResultVerdict.PASS).toList());
        Collections.shuffle(passes, random);
        int sample = passes.isEmpty() ? 0 : Math.max(1, (int) Math.ceil(passes.size() * AUDIT_SHARE));
        passes.subList(0, sample).forEach(TestResult::markAuditSample);

        run.finish(checked == 0 ? RunStatus.FAILED : RunStatus.COMPLETED, score(all), LocalDateTime.now());
    }

    /**
     * Recomputes every verdict of a finished run from the judge's stored findings, after the
     * scenarios' criteria changed (e.g. a criterion moved to good practice). Nothing is asked again
     * and people's reviews are kept. Passes that become new are topped up into the audit sample so it
     * stays at least 10% of passes.
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
            VerdictPolicy.Combined combined = VerdictPolicy.combine(rule, again.verdict(), r.getJudgeReason());
            if (combined.verdict() != r.getVerdict()) {
                changed++;
                String note = again.criteria().stream()
                        .anyMatch(c -> !JudgeService.isCritical(c) && Boolean.FALSE.equals(c.satisfied()))
                        ? "Re-scored: only good-practice criteria were missed. "
                        : "Re-scored. ";
                r.recordVerdict(combined.verdict(), note + combined.detail());
            }
        }
        List<TestResult> passes = new ArrayList<>(all.stream().filter(r -> r.getVerdict() == ResultVerdict.PASS).toList());
        long sampled = passes.stream().filter(TestResult::isAuditSample).count();
        int wanted = passes.isEmpty() ? 0 : Math.max(1, (int) Math.ceil(passes.size() * AUDIT_SHARE));
        List<TestResult> unsampled = new ArrayList<>(passes.stream().filter(r -> !r.isAuditSample()).toList());
        Collections.shuffle(unsampled, random);
        unsampled.stream().limit(Math.max(0, wanted - sampled)).forEach(TestResult::markAuditSample);
        run.rescore(score(all), LocalDateTime.now());
        return changed;
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
