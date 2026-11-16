package com.cognizant.backend.agent;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import com.cognizant.backend.entity.Investigation;
import com.cognizant.backend.entity.InvestigationStatus;
import com.cognizant.backend.entity.Language;
import com.cognizant.backend.entity.ResultVerdict;
import com.cognizant.backend.entity.TestResult;
import com.cognizant.backend.entity.TestRun;
import com.cognizant.backend.entity.TestScenario;
import com.cognizant.backend.exception.NotAllowedException;
import com.cognizant.backend.exception.ResourceNotFoundException;
import com.cognizant.backend.llm.LlmClient;
import com.cognizant.backend.llm.LlmNotConfiguredException;
import com.cognizant.backend.prompt.PromptService;
import com.cognizant.backend.repository.InvestigationRepository;
import com.cognizant.backend.repository.TestResultRepository;

import jakarta.annotation.PreDestroy;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Starts investigations in the background, records the agent's steps as they happen, and reads them back. */
@Service
public class InvestigationService {

    private static final Logger log = LoggerFactory.getLogger(InvestigationService.class);

    private final TestResultRepository results;
    private final InvestigationRepository investigations;
    private final PromptService prompts;
    private final InvestigatorAgent agent;
    private final LlmClient llm;
    private final JsonMapper json;
    private final TransactionTemplate tx;
    private final ExecutorService worker = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "investigator");
        t.setDaemon(true);
        return t;
    });

    public InvestigationService(TestResultRepository results, InvestigationRepository investigations,
            PromptService prompts, InvestigatorAgent agent, LlmClient llm, JsonMapper json, TransactionTemplate tx) {
        this.results = results;
        this.investigations = investigations;
        this.prompts = prompts;
        this.agent = agent;
        this.llm = llm;
        this.json = json;
        this.tx = tx;
    }

    public record View(Long id, Long resultId, String runCode, String scenarioCode, String scenarioTitle,
            String language, String languageName, String status, String agentModel, String conclusion,
            String failureType, String diagnosis, String suggestedFix, List<InvestigationStep> steps, String error,
            String startedBy, Instant startedAt, Instant finishedAt) {
    }

    /** Starts the agent on a result and returns at once; poll {@link #get} for its steps. */
    public View start(Long resultId, String startedBy) {
        if (!llm.isConfigured(LlmClient.Role.JUDGE) || !llm.isConfigured(LlmClient.Role.ASSISTANT)) {
            throw new LlmNotConfiguredException("investigator agent");
        }
        InvestigationContext[] ctx = new InvestigationContext[1];
        Long id = tx.execute(status -> {
            TestResult r = results.findWithScenarioById(resultId)
                    .orElseThrow(() -> new ResourceNotFoundException("Result " + resultId + " not found"));
            if (r.getVerdict() == ResultVerdict.ERROR || r.getAnswer() == null) {
                throw new NotAllowedException("This question was never answered, so there is nothing to investigate.");
            }
            boolean running = investigations.findByResult_IdOrderByStartedAtDesc(resultId).stream()
                    .anyMatch(i -> i.getStatus() == InvestigationStatus.RUNNING);
            if (running) {
                throw new NotAllowedException("An investigation of this result is already running.");
            }
            TestRun run = r.getTestRun();
            Investigation inv = investigations.save(new Investigation(r, run.getJudgeModel(),
                    StringUtils.hasText(startedBy) ? startedBy.strip() : null, Instant.now()));
            ctx[0] = context(inv.getId(), r, run);
            return inv.getId();
        });
        worker.submit(() -> execute(ctx[0]));
        log.info("Started investigation {} of result {}", id, resultId);
        // A self-call skips the proxy, so get()'s own @Transactional would not apply here.
        return tx.execute(status -> get(id));
    }

    private InvestigationContext context(Long investigationId, TestResult r, TestRun run) {
        TestScenario s = r.getScenario();
        String englishQuestion = s.getQuestions().stream()
                .filter(q -> q.getLanguage() == Language.EN)
                .map(q -> q.getText())
                .findFirst().orElse(null);
        String englishAnswer = r.getLanguage() == Language.EN ? null
                : results.findByTestRun_IdOrderByIdAsc(run.getId()).stream()
                        .filter(o -> o.getScenario().getId().equals(s.getId()) && o.getLanguage() == Language.EN
                                && o.getAnswer() != null)
                        .map(TestResult::getAnswer)
                        .findFirst().orElse(null);
        return new InvestigationContext(investigationId, run.getModel(), run.getJudgeModel(),
                prompts.get(run.getPromptVersion()).getText(), s.getTitle(), s.getExpectedBehaviour(), s.getMustDo(),
                s.getMustNotDo(), r.getLanguage(), r.getQuestion(), r.getAnswer(), r.getTranslation(),
                r.getCheckDetail(), r.getJudgeReason(), r.getLanguage() == Language.EN ? null : englishQuestion,
                englishAnswer);
    }

    void execute(InvestigationContext ctx) {
        AtomicReference<List<InvestigationStep>> latest = new AtomicReference<>(List.of());
        try {
            InvestigatorAgent.Outcome outcome = agent.investigate(ctx, steps -> {
                latest.set(steps);
                tx.executeWithoutResult(s -> investigations.findById(ctx.investigationId())
                        .ifPresent(inv -> inv.recordSteps(write(steps))));
            });
            tx.executeWithoutResult(s -> investigations.findById(ctx.investigationId()).ifPresent(inv ->
                    inv.complete(outcome.conclusion(), outcome.failureType(), outcome.diagnosis(),
                            outcome.suggestedFix(), write(outcome.steps()), Instant.now())));
            log.info("Investigation {} concluded {}", ctx.investigationId(), outcome.conclusion());
        } catch (RuntimeException e) {
            log.warn("Investigation {} failed: {}", ctx.investigationId(), e.getMessage());
            tx.executeWithoutResult(s -> investigations.findById(ctx.investigationId())
                    .ifPresent(inv -> inv.fail(e.getMessage(), write(latest.get()), Instant.now())));
        }
    }

    @Transactional(readOnly = true)
    public View get(Long id) {
        Investigation i = investigations.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Investigation " + id + " not found"));
        TestResult r = i.getResult();
        return new View(i.getId(), r.getId(), r.getTestRun().getCode(), r.getScenario().getCode(),
                r.getScenario().getTitle(), r.getLanguage().name(), r.getLanguage().getDisplayName(),
                i.getStatus().name().toLowerCase(Locale.ROOT), i.getAgentModel(), i.getConclusion(),
                i.getFailureType(), i.getDiagnosis(), i.getSuggestedFix(), read(i.getSteps()), i.getError(),
                i.getStartedBy(), i.getStartedAt(), i.getFinishedAt());
    }

    /** Investigations left RUNNING by a restart can never finish. */
    @Transactional
    public int failStale() {
        List<Investigation> stale = investigations.findByStatus(InvestigationStatus.RUNNING);
        stale.forEach(i -> i.fail("The backend restarted before the investigation finished.", null, Instant.now()));
        return stale.size();
    }

    private String write(List<InvestigationStep> steps) {
        return json.writeValueAsString(steps);
    }

    private List<InvestigationStep> read(String stored) {
        if (stored == null || stored.isBlank()) {
            return List.of();
        }
        return json.readValue(stored, new TypeReference<List<InvestigationStep>>() {
        });
    }

    @PreDestroy
    void shutdown() {
        worker.shutdownNow();
    }
}
