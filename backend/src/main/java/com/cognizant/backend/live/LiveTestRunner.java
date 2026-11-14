package com.cognizant.backend.live;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.cognizant.backend.ai.AiAvailability;
import com.cognizant.backend.ai.HealthAssistantService;
import com.cognizant.backend.ai.config.AiProperties;
import com.cognizant.backend.entity.ResultVerdict;
import com.cognizant.backend.entity.RunSource;
import com.cognizant.backend.entity.RunStatus;
import com.cognizant.backend.entity.TestRun;
import com.cognizant.backend.repository.TestRunRepository;
import com.google.genai.errors.ApiException;
import com.google.genai.errors.GenAiIOException;

import jakarta.annotation.PreDestroy;

/**
 * Runs every scenario in every language against the assistant under test, one question at a
 * time on a background thread, saving each answer as soon as it arrives.
 */
@Service
public class LiveTestRunner {

    private static final Logger log = LoggerFactory.getLogger(LiveTestRunner.class);
    /** Attempts per question when the provider rate-limits us (free tiers do this often). */
    static final int MAX_ATTEMPTS = 3;
    static final Duration RATE_LIMIT_BACKOFF = Duration.ofSeconds(20);

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "live-test-run");
        t.setDaemon(true);
        return t;
    });

    private final HealthAssistantService assistant;
    private final SafetyCheck safetyCheck;
    private final LiveRunRecorder recorder;
    private final TestRunRepository runs;
    private final AiAvailability availability;
    private final AiProperties props;

    public LiveTestRunner(HealthAssistantService assistant, SafetyCheck safetyCheck, LiveRunRecorder recorder,
            TestRunRepository runs, AiAvailability availability, AiProperties props) {
        this.assistant = assistant;
        this.safetyCheck = safetyCheck;
        this.recorder = recorder;
        this.runs = runs;
        this.availability = availability;
        this.props = props;
    }

    /** Starts a run and returns immediately; poll the run for progress. One run at a time. */
    public synchronized TestRun start() {
        availability.requireGemini();
        if (runs.existsBySourceAndStatus(RunSource.LIVE, RunStatus.RUNNING)) {
            throw new RunAlreadyActiveException();
        }
        List<PlannedTest> plan = recorder.plan();
        TestRun run = recorder.createRun(props.assistantModel());
        executor.submit(() -> execute(run.getId(), plan));
        log.info("Started live run {} with {} tests", run.getCode(), plan.size());
        return run;
    }

    private void execute(Long runId, List<PlannedTest> plan) {
        try {
            String stopReason = null;
            for (PlannedTest test : plan) {
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
                if (stopReason != null) {
                    // Daily quota is gone: record the rest as skipped instead of calling the API.
                    recorder.saveResult(runId, test, null, props.assistantModel(),
                            new SafetyCheck.Outcome(ResultVerdict.ERROR, "Skipped: " + stopReason), 0, Instant.now());
                    continue;
                }
                stopReason = runOne(runId, test);
            }
        } finally {
            try {
                recorder.finishRun(runId);
            } catch (RuntimeException e) {
                log.error("Could not finish live run {}", runId, e);
            }
        }
    }

    /** @return a reason to stop the whole run (daily quota exhausted), or null to continue */
    private String runOne(Long runId, PlannedTest test) {
        Instant askedAt = Instant.now();
        long start = System.nanoTime();
        String answer = null;
        SafetyCheck.Outcome outcome;
        try {
            answer = askWithRetry(test);
            outcome = safetyCheck.evaluate(test.kind(), answer);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            outcome = new SafetyCheck.Outcome(ResultVerdict.ERROR, "Run was stopped before this question finished.");
        } catch (RuntimeException e) {
            log.warn("Live test failed for scenario {} ({})", test.scenarioId(), test.language(), e);
            outcome = new SafetyCheck.Outcome(ResultVerdict.ERROR, "Model call failed: " + describe(e));
        }
        long durationMs = (System.nanoTime() - start) / 1_000_000;
        recorder.saveResult(runId, test, answer, props.assistantModel(), outcome, durationMs, askedAt);
        return outcome.verdict() == ResultVerdict.ERROR && outcome.detail().contains(DAILY_QUOTA)
                ? DAILY_QUOTA + " for " + props.assistantModel() + "."
                : null;
    }

    static final String DAILY_QUOTA = "daily model quota exhausted";

    /** Free tiers have per-day request limits; once hit, retrying within the run cannot succeed. */
    static boolean isDailyQuota(ApiException e) {
        String message = String.valueOf(e.getMessage()) + " " + String.valueOf(e.message());
        return e.code() == 429 && (message.contains("PerDay") || message.toLowerCase().contains("per day"));
    }

    private String askWithRetry(PlannedTest test) throws InterruptedException {
        for (int attempt = 1;; attempt++) {
            try {
                return assistant.ask(test.question(), test.language()).answer();
            } catch (ApiException e) {
                if (e.code() != 429 || attempt >= MAX_ATTEMPTS || isDailyQuota(e)) {
                    throw e;
                }
                log.info("Rate limited by the model provider; retrying in {}s", RATE_LIMIT_BACKOFF.toSeconds() * attempt);
                Thread.sleep(RATE_LIMIT_BACKOFF.multipliedBy(attempt));
            }
        }
    }

    private static String describe(RuntimeException e) {
        if (e instanceof ApiException api) {
            if (isDailyQuota(api)) {
                return "HTTP 429, " + DAILY_QUOTA + " (free tier allows a fixed number of requests per day).";
            }
            return "HTTP " + api.code() + (api.code() == 429 ? " (rate limited after " + MAX_ATTEMPTS + " attempts)"
                    : " " + api.status());
        }
        if (e instanceof GenAiIOException) {
            return "could not reach Gemini or the call timed out";
        }
        String message = e.getMessage();
        return e.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
