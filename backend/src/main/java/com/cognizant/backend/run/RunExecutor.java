package com.cognizant.backend.run;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.cognizant.backend.assistant.AssistantService;
import com.cognizant.backend.check.RuleCheck;
import com.cognizant.backend.judge.JudgeService;
import com.cognizant.backend.judge.JudgeVerdict;
import com.cognizant.backend.llm.LlmException;
import com.cognizant.backend.llm.LlmProperties;
import com.cognizant.backend.llm.LlmReply;

import jakarta.annotation.PreDestroy;

/**
 * Asks a run's questions on background threads, a few at a time, and saves each checked answer as
 * soon as it is ready. One failed question never stops the run: it is recorded as an error.
 */
@Component
public class RunExecutor {

    private static final Logger log = LoggerFactory.getLogger(RunExecutor.class);

    private final AssistantService assistant;
    private final RuleCheck rules;
    private final JudgeService judge;
    private final RunRecorder recorder;
    private final ExecutorService workers;

    public RunExecutor(AssistantService assistant, RuleCheck rules, JudgeService judge, RunRecorder recorder,
            LlmProperties props) {
        this.assistant = assistant;
        this.rules = rules;
        this.judge = judge;
        this.recorder = recorder;
        AtomicInteger n = new AtomicInteger();
        this.workers = Executors.newFixedThreadPool(props.concurrency(), r -> {
            Thread t = new Thread(r, "run-worker-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
    }

    /** Starts the run in the background and returns at once. */
    public void execute(Long runId, String model, String judgeModel, String systemPrompt, List<PlannedTest> plan) {
        CompletableFuture<?>[] tasks = plan.stream()
                .map(test -> CompletableFuture.runAsync(() -> runOne(runId, model, judgeModel, systemPrompt, test),
                        workers))
                .toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(tasks).whenComplete((ok, error) -> {
            try {
                recorder.finishRun(runId);
                log.info("Run {} finished ({} questions)", runId, plan.size());
            } catch (RuntimeException e) {
                log.error("Could not finish run {}", runId, e);
            }
        });
    }

    void runOne(Long runId, String model, String judgeModel, String systemPrompt, PlannedTest test) {
        Instant askedAt = Instant.now();
        long start = System.nanoTime();
        LlmReply reply;
        try {
            reply = assistant.ask(model, systemPrompt, test.question());
        } catch (RuntimeException e) {
            log.warn("Question failed: scenario {} {} attempt {}: {}", test.scenarioId(), test.language(),
                    test.attempt(), e.getMessage());
            recorder.saveError(runId, test, model, elapsedMs(start), askedAt, describe(e));
            return;
        }
        long durationMs = elapsedMs(start);

        RuleCheck.Outcome rule = rules.check(test.kind(), test.signals(), test.language(), reply.content());
        if (reply.wasCutOff()) {
            rule = new RuleCheck.Outcome(rule.verdict(), rule.detail() + " Answer was cut off at the length limit.");
        }

        JudgeVerdict verdict = null;
        String judgeError = null;
        if (!reply.content().isBlank()) {
            try {
                verdict = judge.judge(test.judgeRequest(reply.content()), judgeModel);
            } catch (RuntimeException e) {
                log.warn("Judge failed: scenario {} {}: {}", test.scenarioId(), test.language(), e.getMessage());
                judgeError = "Judge call failed: " + describe(e);
            }
        } else {
            judgeError = "Nothing to judge: the answer was empty.";
        }
        try {
            recorder.saveChecked(runId, test, model, reply.content(), durationMs, askedAt, rule, verdict, judgeError);
        } catch (RuntimeException e) {
            log.error("Could not save result: scenario {} {}", test.scenarioId(), test.language(), e);
        }
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    static String describe(RuntimeException e) {
        if (e instanceof LlmException llm) {
            return llm.getMessage();
        }
        String message = e.getMessage();
        return e.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }

    @PreDestroy
    void shutdown() {
        workers.shutdownNow();
    }
}
