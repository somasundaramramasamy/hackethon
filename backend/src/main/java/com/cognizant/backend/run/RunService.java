package com.cognizant.backend.run;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import com.cognizant.backend.entity.AssistantPrompt;
import com.cognizant.backend.entity.Language;
import com.cognizant.backend.entity.ResultVerdict;
import com.cognizant.backend.entity.RunStatus;
import com.cognizant.backend.entity.RunType;
import com.cognizant.backend.entity.ScenarioKind;
import com.cognizant.backend.entity.TestResult;
import com.cognizant.backend.entity.TestRun;
import com.cognizant.backend.entity.TestScenario;
import com.cognizant.backend.exception.NotAllowedException;
import com.cognizant.backend.exception.ResourceNotFoundException;
import com.cognizant.backend.fix.ScenarioFixService;
import com.cognizant.backend.llm.LlmClient;
import com.cognizant.backend.llm.LlmNotConfiguredException;
import com.cognizant.backend.llm.LlmProperties;
import com.cognizant.backend.llm.ModelOption;
import com.cognizant.backend.prompt.PromptService;
import com.cognizant.backend.repository.TestResultRepository;
import com.cognizant.backend.repository.TestRunRepository;
import com.cognizant.backend.repository.TestScenarioRepository;

/** Starts runs: baselines with the active prompt, and retests of a proposed fix. One run at a time. */
@Service
public class RunService {

    private static final Logger log = LoggerFactory.getLogger(RunService.class);

    private final TestScenarioRepository scenarios;
    private final TestRunRepository runs;
    private final TestResultRepository results;
    private final PromptService prompts;
    private final RunRecorder recorder;
    private final RunExecutor executor;
    private final LlmProperties props;
    private final LlmClient llm;
    private final TransactionTemplate tx;
    private final ScenarioFixService fixes;

    public RunService(TestScenarioRepository scenarios, TestRunRepository runs, TestResultRepository results,
            PromptService prompts, RunRecorder recorder, RunExecutor executor, LlmProperties props, LlmClient llm,
            TransactionTemplate tx, ScenarioFixService fixes) {
        this.scenarios = scenarios;
        this.runs = runs;
        this.results = results;
        this.prompts = prompts;
        this.recorder = recorder;
        this.executor = executor;
        this.props = props;
        this.llm = llm;
        this.tx = tx;
        this.fixes = fixes;
    }

    /** Asks every selected active scenario with the prompt version in use. */
    public synchronized TestRun startBaseline(StartRunRequest request) {
        requireConfigured();
        requireIdle();
        String model = pick(request.model(), props.assistantModels().getFirst().id(), props.offersAssistant(request.model()),
                "assistant");
        String judgeModel = pick(request.judgeModel(), props.judgeModels().getFirst().id(),
                props.offersJudge(request.judgeModel()), "judge");
        int repeats = request.repeats() == null ? 1 : request.repeats();
        Set<Language> languages = request.languages() == null || request.languages().isEmpty()
                ? EnumSet.allOf(Language.class)
                : EnumSet.copyOf(request.languages());
        AssistantPrompt prompt = prompts.active();

        List<PlannedTest> plan = tx.execute(status -> {
            List<TestScenario> chosen = scenarios.findAllByActiveTrueOrderByPositionAsc().stream()
                    .filter(s -> request.scenarios() == null || request.scenarios().isEmpty()
                            || request.scenarios().contains(s.getCode()))
                    .toList();
            Map<String, ScenarioFixService.Guidance> guidance = fixes.activeGuidance();
            List<PlannedTest> tests = new ArrayList<>();
            for (int attempt = 1; attempt <= repeats; attempt++) {
                for (TestScenario s : chosen) {
                    for (var q : s.getQuestions()) {
                        if (languages.contains(q.getLanguage())) {
                            tests.add(planned(s, q.getLanguage(), q.getText(), attempt, guidance));
                        }
                    }
                }
            }
            return tests;
        });
        if (plan == null || plan.isEmpty()) {
            throw new InvalidRunRequestException("Nothing to ask: no active scenario matches the selection.");
        }
        long scenarioCount = plan.stream().map(PlannedTest::scenarioId).distinct().count();
        String suite = "Baseline: " + scenarioCount + (scenarioCount == 1 ? " scenario, " : " scenarios, ")
                + languages.size() + (languages.size() == 1 ? " language" : " languages")
                + (repeats > 1 ? ", ×" + repeats : "");
        String name = clean(request.name()) != null ? clean(request.name())
                : defaultName(props.assistantModels(), model, languages.size(), repeats);
        TestRun run = recorder.createRun(RunType.BASELINE, name, suite, model, judgeModel, prompt.getVersion(), null,
                repeats, plan.size(), clean(request.startedBy()));
        executor.execute(run.getId(), model, judgeModel, prompt.getText(), plan);
        log.info("Started baseline run {} with {} questions (prompt v{}, {} judged by {})", run.getCode(),
                plan.size(), prompt.getVersion(), model, judgeModel);
        return run;
    }

    /**
     * Retests a prompt version against the run it was meant to fix: every question that did not pass
     * there, plus every English question and every control question, so a fix cannot hide a regression.
     */
    public synchronized TestRun startRetest(String sourceRunCode, AssistantPrompt prompt, String startedBy) {
        requireConfigured();
        requireIdle();
        record Plan(TestRun source, List<PlannedTest> tests) {
        }
        Plan built = tx.execute(status -> {
            TestRun source = runs.findByCode(sourceRunCode)
                    .orElseThrow(() -> new ResourceNotFoundException("Run " + sourceRunCode + " not found"));
            List<TestResult> sourceResults = results.findByTestRun_IdOrderByIdAsc(source.getId());

            // One cell per scenario and language, keeping the question exactly as it was asked.
            Map<String, TestResult> cells = new LinkedHashMap<>();
            Map<String, Boolean> failed = new LinkedHashMap<>();
            for (TestResult r : sourceResults) {
                String key = r.getScenario().getId() + ":" + r.getLanguage();
                cells.putIfAbsent(key, r);
                failed.merge(key, r.getEffectiveVerdict() != ResultVerdict.PASS, Boolean::logicalOr);
            }
            Map<String, ScenarioFixService.Guidance> guidance = fixes.activeGuidance();
            List<PlannedTest> tests = new ArrayList<>();
            for (int attempt = 1; attempt <= source.getRepeats(); attempt++) {
                for (var e : cells.entrySet()) {
                    TestResult r = e.getValue();
                    boolean include = failed.get(e.getKey()) || r.getLanguage() == Language.EN
                            || r.getScenario().getKind() == ScenarioKind.CONTROL;
                    if (include) {
                        tests.add(planned(r.getScenario(), r.getLanguage(), r.getQuestion(), attempt, guidance));
                    }
                }
            }
            tests.sort(Comparator.comparingInt(PlannedTest::attempt));
            return new Plan(source, tests);
        });
        if (built.tests().isEmpty()) {
            throw new InvalidRunRequestException("Run " + sourceRunCode + " has no results to retest.");
        }
        TestRun source = built.source();
        TestRun run = recorder.createRun(RunType.RETEST, "Retest of fix v" + prompt.getVersion(),
                "Retest of " + source.getCode() + " with prompt v" + prompt.getVersion(), source.getModel(),
                source.getJudgeModel(), prompt.getVersion(), source.getId(), source.getRepeats(),
                built.tests().size(), clean(startedBy));
        executor.execute(run.getId(), source.getModel(), source.getJudgeModel(), prompt.getText(), built.tests());
        log.info("Started retest {} of {} with prompt v{} ({} questions)", run.getCode(), source.getCode(),
                prompt.getVersion(), built.tests().size());
        return run;
    }

    /**
     * Recomputes a finished run's verdicts from the judge's stored findings against the scenarios'
     * current criteria. Synchronized with starting runs, so it never races a run that is writing.
     *
     * @return how many verdicts changed
     */
    public synchronized int rescore(String code) {
        TestRun run = runs.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Run " + code + " not found"));
        if (run.getStatus() == RunStatus.RUNNING) {
            throw new NotAllowedException("Run " + code + " is still running; re-score it when it has finished.");
        }
        return recorder.rescore(run.getId());
    }

    /** Gives a run the name people will know it by. */
    public void rename(String code, String name) {
        String clean = clean(name);
        if (clean == null) {
            throw new InvalidRunRequestException("Give the test a name.");
        }
        tx.executeWithoutResult(status -> runs.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Run " + code + " not found"))
                .rename(clean));
    }

    /** A name for a run started without one, e.g. "Gemma 4 31B · 4 languages · ×3". */
    static String defaultName(List<ModelOption> models, String model, int languages, int repeats) {
        String label = models.stream().filter(m -> m.id().equals(model)).findFirst()
                .map(m -> m.label().replaceAll("\\s*\\(.*\\)$", ""))
                .orElse(model);
        return label + " · " + languages + (languages == 1 ? " language" : " languages")
                + (repeats > 1 ? " · ×" + repeats : "");
    }

    public boolean isRunning() {
        return runs.existsByStatus(RunStatus.RUNNING);
    }

    private void requireIdle() {
        // A run that crashed elsewhere would otherwise block new runs until someone restarts a backend.
        recorder.failStaleRuns();
        if (isRunning()) {
            throw new RunAlreadyActiveException();
        }
    }

    private void requireConfigured() {
        if (!llm.isConfigured(LlmClient.Role.ASSISTANT)) {
            throw new LlmNotConfiguredException("assistant under test");
        }
        if (!llm.isConfigured(LlmClient.Role.JUDGE)) {
            throw new LlmNotConfiguredException("judge");
        }
    }

    /** One question, with the fix saved for its scenario and language, so the assistant can avoid the earlier failure. */
    private static PlannedTest planned(TestScenario s, Language language, String question, int attempt,
            Map<String, ScenarioFixService.Guidance> guidance) {
        return new PlannedTest(s.getId(), s.getTitle(), s.getKind(), s.getRuleSignals(), s.getExpectedBehaviour(),
                s.getMustDo(), s.getMustNotDo(), s.getShouldDo(), s.getSourceName(), language, question, attempt,
                guidance.get(ScenarioFixService.key(s.getId(), language)));
    }

    private static String pick(String requested, String fallback, boolean offered, String role) {
        if (!StringUtils.hasText(requested)) {
            return fallback;
        }
        if (!offered) {
            throw new InvalidRunRequestException("'" + requested + "' is not an offered " + role + " model.");
        }
        return requested;
    }

    private static String clean(String name) {
        return StringUtils.hasText(name) ? name.strip() : null;
    }
}
