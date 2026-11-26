package com.cognizant.backend.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import com.cognizant.backend.entity.AssistantPrompt;
import com.cognizant.backend.entity.InvestigationStatus;
import com.cognizant.backend.entity.PromptStatus;
import com.cognizant.backend.entity.ResultVerdict;
import com.cognizant.backend.entity.RunStatus;
import com.cognizant.backend.entity.RunType;
import com.cognizant.backend.entity.TestResult;
import com.cognizant.backend.entity.TestRun;
import com.cognizant.backend.exception.NotAllowedException;
import com.cognizant.backend.exception.ResourceNotFoundException;
import com.cognizant.backend.llm.LlmClient;
import com.cognizant.backend.llm.LlmNotConfiguredException;
import com.cognizant.backend.prompt.PromptService;
import com.cognizant.backend.repository.AssistantPromptRepository;
import com.cognizant.backend.repository.InvestigationRepository;
import com.cognizant.backend.repository.TestResultRepository;
import com.cognizant.backend.repository.TestRunRepository;
import com.cognizant.backend.run.InvalidRunRequestException;
import com.cognizant.backend.run.RunAlreadyActiveException;
import com.cognizant.backend.run.RunService;

/**
 * The fix loop: the agent proposes a prompt version from a run's failures, a named person approves
 * or rejects it, approval starts a retest, and a person adopts the version after seeing the results.
 */
@Service
public class FixService {

    private static final Logger log = LoggerFactory.getLogger(FixService.class);

    /** Failures sent to the agent at most; enough to show every pattern without a huge prompt. */
    static final int MAX_FAILURES = 24;
    /** Earlier rejected proposals shown to the agent, newest last. */
    static final int MAX_REJECTIONS = 3;
    static final int EXCERPT_CHARS = 500;
    /** Newly broken answers shown per earlier retested proposal. */
    static final int MAX_BROKEN = 8;

    private final TestRunRepository runs;
    private final TestResultRepository results;
    private final InvestigationRepository investigations;
    private final PromptService prompts;
    private final AssistantPromptRepository promptRepository;
    private final RunService runService;
    private final FixAgent agent;
    private final LlmClient llm;
    private final TransactionTemplate tx;

    public FixService(TestRunRepository runs, TestResultRepository results, InvestigationRepository investigations,
            PromptService prompts, AssistantPromptRepository promptRepository, RunService runService, FixAgent agent,
            LlmClient llm, TransactionTemplate tx) {
        this.runs = runs;
        this.results = results;
        this.investigations = investigations;
        this.prompts = prompts;
        this.promptRepository = promptRepository;
        this.runService = runService;
        this.agent = agent;
        this.llm = llm;
        this.tx = tx;
    }

    private record Input(TestRun run, String currentPrompt, List<FixAgent.Failure> failures,
            List<FixAgent.Diagnosis> diagnoses, List<FixAgent.Rejection> rejections, List<FixAgent.Tried> tried) {
    }

    /**
     * Earlier proposals for this run that were approved and retested but are not in use, newest last, each with
     * the answers its retest newly broke. The agent learns from the evidence, not only from a person's note.
     */
    private List<FixAgent.Tried> tried(TestRun run) {
        List<AssistantPrompt> candidates = promptRepository.findBySourceRunIdAndBasedOnVersionAndStatusInOrderByVersionAsc(
                run.getId(), run.getPromptVersion(), List.of(PromptStatus.APPROVED, PromptStatus.RETIRED));
        Map<String, Boolean> failedBefore = failingCells(results.findByTestRun_IdOrderByIdAsc(run.getId()));
        List<FixAgent.Tried> tried = new ArrayList<>();
        for (AssistantPrompt p : candidates) {
            TestRun retest = runs.findByTypeAndPromptVersionOrderByStartedAtDesc(RunType.RETEST, p.getVersion()).stream()
                    .filter(r -> run.getId().equals(r.getBaselineRunId()) && r.getStatus() == RunStatus.COMPLETED)
                    .findFirst().orElse(null);
            if (retest == null) {
                continue;
            }
            List<TestResult> after = results.findByTestRun_IdOrderByIdAsc(retest.getId());
            Map<String, Boolean> failsNow = failingCells(after);
            int fixed = 0;
            int still = 0;
            Map<String, TestResult> broke = new LinkedHashMap<>();
            for (var cell : failsNow.entrySet()) {
                boolean before = failedBefore.getOrDefault(cell.getKey(), false);
                if (before && !cell.getValue()) {
                    fixed++;
                } else if (before) {
                    still++;
                } else if (cell.getValue()) {
                    after.stream()
                            .filter(r -> cellKey(r).equals(cell.getKey())
                                    && r.getEffectiveVerdict() != ResultVerdict.PASS)
                            .findFirst().ifPresent(r -> broke.put(cell.getKey(), r));
                }
            }
            List<FixAgent.Failure> brokeList = broke.values().stream().limit(MAX_BROKEN)
                    .map(r -> new FixAgent.Failure(r.getScenario().getCode() + " " + r.getScenario().getTitle(),
                            r.getLanguage().getDisplayName(), r.getQuestion(), excerpt(r.getAnswer()),
                            excerpt(r.getTranslation()), r.getCheckDetail(), r.getReviewNote()))
                    .toList();
            if (broke.isEmpty()) {
                // A retest that broke nothing is not a lesson in what to avoid.
                continue;
            }
            tried.add(new FixAgent.Tried(p.getVersion(), p.getText(), p.getSummary(), retest.getCode(), fixed, still,
                    brokeList));
        }
        return tried.subList(Math.max(0, tried.size() - MAX_REJECTIONS), tried.size());
    }

    /** For each scenario and language in a run: whether any of its answers did not pass. */
    private static Map<String, Boolean> failingCells(List<TestResult> all) {
        Map<String, Boolean> cells = new LinkedHashMap<>();
        for (TestResult r : all) {
            cells.merge(cellKey(r), r.getEffectiveVerdict() != ResultVerdict.PASS, Boolean::logicalOr);
        }
        return cells;
    }

    private static String cellKey(TestResult r) {
        return r.getScenario().getCode() + ":" + r.getLanguage();
    }

    /**
     * Whether an answer counts as a failure the fix must cover: a person said unsafe, or nobody reviewed it and
     * the checks failed it or could not decide.
     */
    static boolean isFailing(TestResult r) {
        return r.getHumanVerdict() == ResultVerdict.FAIL
                || (r.getHumanVerdict() == null
                        && (r.getVerdict() == ResultVerdict.FAIL || r.getVerdict() == ResultVerdict.NEEDS_REVIEW));
    }

    /** Asks the agent for a revised prompt that fixes the run's failures. Takes about half a minute. */
    public AssistantPrompt propose(String runCode, String requestedBy) {
        if (!llm.isConfigured(LlmClient.Role.JUDGE)) {
            throw new LlmNotConfiguredException("fix agent");
        }
        Input input = tx.execute(status -> {
            TestRun run = runs.findByCode(runCode)
                    .orElseThrow(() -> new ResourceNotFoundException("Run " + runCode + " not found"));
            if (run.getStatus() == RunStatus.RUNNING) {
                throw new InvalidRunRequestException("Wait for the run to finish before proposing a fix.");
            }
            Map<String, TestResult> cells = new LinkedHashMap<>();
            for (TestResult r : results.findByTestRun_IdOrderByIdAsc(run.getId())) {
                if (isFailing(r)) {
                    cells.putIfAbsent(r.getScenario().getCode() + ":" + r.getLanguage(), r);
                }
            }
            if (cells.isEmpty()) {
                throw new InvalidRunRequestException("Run " + runCode + " has no failures to fix.");
            }
            List<FixAgent.Failure> failures = new ArrayList<>();
            cells.values().stream().limit(MAX_FAILURES).forEach(r -> failures.add(new FixAgent.Failure(
                    r.getScenario().getCode() + " " + r.getScenario().getTitle(), r.getLanguage().getDisplayName(),
                    r.getQuestion(), excerpt(r.getAnswer()), excerpt(r.getTranslation()), r.getCheckDetail(),
                    r.getReviewNote())));
            List<FixAgent.Diagnosis> diagnoses = investigations
                    .findByResult_TestRun_IdAndStatusOrderByStartedAtAsc(run.getId(), InvestigationStatus.COMPLETED)
                    .stream()
                    .map(i -> new FixAgent.Diagnosis(
                            i.getResult().getScenario().getCode() + " " + i.getResult().getScenario().getTitle(),
                            i.getResult().getLanguage().getDisplayName(), i.getConclusion(), i.getFailureType(),
                            i.getDiagnosis(), i.getSuggestedFix()))
                    .toList();
            List<FixAgent.Rejection> rejections = promptRepository
                    .findByStatusAndSourceRunIdAndBasedOnVersionOrderByVersionAsc(PromptStatus.REJECTED, run.getId(),
                            run.getPromptVersion())
                    .stream()
                    .map(p -> new FixAgent.Rejection(p.getVersion(), p.getText(), p.getSummary(), p.getDecidedBy(),
                            p.getDecisionNote()))
                    .toList();
            // Only the latest few, so the brief stays focused.
            rejections = rejections.subList(Math.max(0, rejections.size() - MAX_REJECTIONS), rejections.size());
            List<FixAgent.Tried> tried = tried(run);
            return new Input(run, prompts.get(run.getPromptVersion()).getText(), failures, diagnoses, rejections,
                    tried);
        });

        FixAgent.Proposal proposal = agent.propose(input.run().getJudgeModel(), input.currentPrompt(),
                input.failures(), input.diagnoses(), input.rejections(), input.tried());
        AssistantPrompt saved = prompts.propose(proposal.prompt(), proposal.summary(), proposal.rationale(),
                input.run().getPromptVersion(), input.run().getId(), "agent:" + input.run().getJudgeModel());
        log.info("Fix agent proposed prompt v{} for run {} ({} failures, {} diagnoses){}", saved.getVersion(),
                runCode, input.failures().size(), input.diagnoses().size(),
                StringUtils.hasText(requestedBy) ? ", requested by " + requestedBy : "");
        return saved;
    }

    /** A named person approves the proposal; its retest starts at once if no other run is in progress. */
    public AssistantPrompt approve(int version, String person, String note) {
        AssistantPrompt approved = prompts.approve(version, requireName(person), note);
        try {
            startRetest(approved, person);
        } catch (RunAlreadyActiveException e) {
            // The approval stands; the retest can be started from the prompt's page once the other run ends.
            log.info("Prompt v{} approved, but its retest waits: another run is in progress", version);
        }
        return prompts.get(version);
    }

    public AssistantPrompt reject(int version, String person, String note) {
        return prompts.reject(version, requireName(person), note);
    }

    /** Starts (or restarts) the retest of an approved version, e.g. if another run was busy at approval. */
    public void retest(int version, String person) {
        AssistantPrompt p = prompts.get(version);
        if (p.getStatus() != PromptStatus.APPROVED) {
            throw new NotAllowedException("Only an approved version can be retested.");
        }
        startRetest(p, person);
    }

    public AssistantPrompt adopt(int version, String person) {
        return prompts.adopt(version, requireName(person));
    }

    private void startRetest(AssistantPrompt prompt, String person) {
        if (prompt.getSourceRunId() == null) {
            throw new NotAllowedException("Version " + prompt.getVersion() + " is not linked to a run to retest.");
        }
        String sourceCode = runs.findById(prompt.getSourceRunId()).map(TestRun::getCode)
                .orElseThrow(() -> new ResourceNotFoundException("The run this fix was made for is gone"));
        runService.startRetest(sourceCode, prompt, person);
    }

    private static String requireName(String person) {
        if (!StringUtils.hasText(person)) {
            throw new InvalidRunRequestException("A named person must make this decision.");
        }
        return person.strip();
    }

    private static String excerpt(String text) {
        if (text == null) {
            return null;
        }
        return text.length() > EXCERPT_CHARS ? text.substring(0, EXCERPT_CHARS) + "…" : text;
    }
}
