package com.cognizant.backend.live;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cognizant.backend.entity.FailureCategory;
import com.cognizant.backend.entity.FailureCount;
import com.cognizant.backend.entity.Language;
import com.cognizant.backend.entity.LanguageResult;
import com.cognizant.backend.entity.ResultVerdict;
import com.cognizant.backend.entity.RunSource;
import com.cognizant.backend.entity.RunStatus;
import com.cognizant.backend.entity.ScenarioKind;
import com.cognizant.backend.entity.TestResult;
import com.cognizant.backend.entity.TestRun;
import com.cognizant.backend.repository.TestResultRepository;
import com.cognizant.backend.repository.TestRunRepository;
import com.cognizant.backend.repository.TestScenarioRepository;

/** Database writes for live runs. Each method is its own transaction so progress is visible as it happens. */
@Service
public class LiveRunRecorder {

    static final String SUITE = "Live: emergency & control scenarios";
    private static final DateTimeFormatter CODE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final TestRunRepository runs;
    private final TestScenarioRepository scenarios;
    private final TestResultRepository results;

    public LiveRunRecorder(TestRunRepository runs, TestScenarioRepository scenarios, TestResultRepository results) {
        this.runs = runs;
        this.scenarios = scenarios;
        this.results = results;
    }

    @Transactional(readOnly = true)
    public List<PlannedTest> plan() {
        List<PlannedTest> plan = new ArrayList<>();
        scenarios.findAllByActiveTrueOrderByPositionAsc().forEach(s -> s.getQuestions()
                .forEach(q -> plan.add(new PlannedTest(s.getId(), s.getKind(), q.getLanguage(), q.getText()))));
        return plan;
    }

    @Transactional
    public TestRun createRun(String model) {
        LocalDateTime now = LocalDateTime.now();
        return runs.save(TestRun.startLive("LR-" + now.format(CODE_FORMAT), SUITE, model, now));
    }

    @Transactional
    public void saveResult(Long runId, PlannedTest test, String answer, String model, SafetyCheck.Outcome outcome,
            long durationMs, Instant askedAt) {
        results.save(new TestResult(runs.getReferenceById(runId), scenarios.getReferenceById(test.scenarioId()),
                test.language(), test.question(), answer, model, outcome.verdict(), outcome.detail(), durationMs,
                askedAt));
    }

    /** Totals the results per language, classifies failures and closes the run. */
    @Transactional
    public void finishRun(Long runId) {
        TestRun run = runs.findById(runId).orElseThrow();
        List<TestResult> all = results.findByTestRun_IdOrderByIdAsc(runId);

        Map<Language, int[]> perLanguage = new EnumMap<>(Language.class); // [total, passed, failed]
        for (TestResult r : all) {
            int[] c = perLanguage.computeIfAbsent(r.getLanguage(), k -> new int[3]);
            c[0]++;
            if (r.getVerdict() == ResultVerdict.PASS) {
                c[1]++;
            } else if (r.getVerdict() == ResultVerdict.FAIL) {
                c[2]++;
            }
        }
        List<LanguageResult> languageResults = perLanguage.entrySet().stream()
                .map(e -> new LanguageResult(e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2]))
                .toList();

        int passed = perLanguage.values().stream().mapToInt(c -> c[1]).sum();
        int failed = perLanguage.values().stream().mapToInt(c -> c[2]).sum();
        double score = passed + failed == 0 ? 0 : Math.round(1000.0 * passed / (passed + failed)) / 10.0;
        RunStatus status = passed + failed == 0 ? RunStatus.FAILED : RunStatus.COMPLETED;
        run.finish(status, score, languageResults, classifyFailures(all), LocalDateTime.now());
    }

    /**
     * A failure in Tamil or Hindi where the English answer to the same scenario passed is a
     * cross-language inconsistency, the gap Kavasam exists to find. Other failures are classified
     * by scenario kind.
     */
    static List<FailureCount> classifyFailures(List<TestResult> all) {
        Set<Long> englishPassed = all.stream()
                .filter(r -> r.getLanguage() == Language.EN && r.getVerdict() == ResultVerdict.PASS)
                .map(r -> r.getScenario().getId())
                .collect(Collectors.toSet());
        Map<FailureCategory, Integer> counts = new EnumMap<>(FailureCategory.class);
        for (TestResult r : all) {
            if (r.getVerdict() != ResultVerdict.FAIL) {
                continue;
            }
            FailureCategory category = r.getLanguage() != Language.EN && englishPassed.contains(r.getScenario().getId())
                    ? FailureCategory.CROSS_LANGUAGE_INCONSISTENCY
                    : r.getScenario().getKind() == ScenarioKind.CONTROL
                            ? FailureCategory.NEEDLESS_ESCALATION
                            : FailureCategory.INCOMPLETE_OR_UNSAFE;
            counts.merge(category, 1, Integer::sum);
        }
        return counts.entrySet().stream().map(e -> new FailureCount(e.getKey(), e.getValue())).toList();
    }

    /** Runs left RUNNING by a restart can never finish; close them so a new run can start. */
    @Transactional
    public int failStaleRuns() {
        List<TestRun> stale = runs.findBySourceAndStatus(RunSource.LIVE, RunStatus.RUNNING);
        stale.forEach(r -> r.markFailed(LocalDateTime.now()));
        return stale.size();
    }
}
