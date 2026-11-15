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
import com.cognizant.backend.exception.ResourceNotFoundException;
import com.cognizant.backend.llm.LlmClient;
import com.cognizant.backend.llm.LlmNotConfiguredException;
import com.cognizant.backend.llm.LlmProperties;
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

    public RunService(TestScenarioRepository scenarios, TestRunRepository runs, TestResultRepository results,
            PromptService prompts, RunRecorder recorder, RunExecutor executor, LlmProperties props, LlmClient llm,
            TransactionTemplate tx) {
        this.scenarios = scenarios;
        this.runs = runs;
        this.results = results;
        this.prompts = prompts;
        this.recorder = recorder;
        this.executor = executor;
        this.props = props;
        this.llm = llm;
        this.tx = tx;
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
            List<PlannedTest> tests = new ArrayList<>();
            for (int attempt = 1; attempt <= repeats; attempt++) {
                for (TestScenario s : chosen) {
                    for (var q : s.getQuestions()) {
                        if (languages.contains(q.getLanguage())) {
                            tests.add(planned(s, q.getLanguage(), q.getText(), attempt));
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
        String suite = "Baseline: " + scenarioCount + " scenarios, " + languages.size() + " languages"
                + (repeats > 1 ? ", ×" + repeats : "");
        TestRun run = recorder.createRun(RunType.BASELINE, suite, model, judgeModel, prompt.getVersion(), null,
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
            List<PlannedTest> tests = new ArrayList<>();
            for (int attempt = 1; attempt <= source.getRepeats(); attempt++) {
                for (var e : cells.entrySet()) {
                    TestResult r = e.getValue();
                    boolean include = failed.get(e.getKey()) || r.getLanguage() == Language.EN
                            || r.getScenario().getKind() == ScenarioKind.CONTROL;
                    if (include) {
                        tests.add(planned(r.getScenario(), r.getLanguage(), r.getQuestion(), attempt));
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
        TestRun run = recorder.createRun(RunType.RETEST,
                "Retest of " + source.getCode() + " with prompt v" + prompt.getVersion(), source.getModel(),
                source.getJudgeModel(), prompt.getVersion(), source.getId(), source.getRepeats(),
                built.tests().size(), clean(startedBy));
        executor.execute(run.getId(), source.getModel(), source.getJudgeModel(), prompt.getText(), built.tests());
        log.info("Started retest {} of {} with prompt v{} ({} questions)", run.getCode(), source.getCode(),
                prompt.getVersion(), built.tests().size());
        return run;
    }

    public boolean isRunning() {
        return runs.existsByStatus(RunStatus.RUNNING);
    }

    private void requireIdle() {
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

    private static PlannedTest planned(TestScenario s, Language language, String question, int attempt) {
        return new PlannedTest(s.getId(), s.getTitle(), s.getKind(), s.getRuleSignals(), s.getExpectedBehaviour(),
                s.getMustDo(), s.getMustNotDo(), s.getSourceName(), language, question, attempt);
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
