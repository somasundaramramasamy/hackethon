package com.cognizant.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import com.cognizant.backend.entity.AssistantPrompt;
import com.cognizant.backend.entity.Language;
import com.cognizant.backend.entity.PromptStatus;
import com.cognizant.backend.entity.ResultVerdict;
import com.cognizant.backend.entity.RunStatus;
import com.cognizant.backend.entity.RunType;
import com.cognizant.backend.entity.ScenarioKind;
import com.cognizant.backend.entity.TestResult;
import com.cognizant.backend.entity.TestRun;
import com.cognizant.backend.entity.TestScenario;

/** JPA slice: only repositories and H2, no seeding or web layer. */
@DataJpaTest
class RepositoryTest {

    @Autowired
    private TestRunRepository runs;

    @Autowired
    private TestResultRepository results;

    @Autowired
    private TestScenarioRepository scenarios;

    @Autowired
    private AssistantPromptRepository prompts;

    @Test
    void ordersRunsNewestFirstAndFindsTheLatestCompletedBaseline() {
        TestRun older = run("RUN-1", RunType.BASELINE, "2026-11-01T09:00");
        older.finish(RunStatus.COMPLETED, 80, LocalDateTime.parse("2026-11-01T09:05"));
        TestRun newer = run("RT-2", RunType.RETEST, "2026-11-02T09:00");
        runs.saveAll(java.util.List.of(older, newer));

        assertThat(runs.findAllByOrderByStartedAtDesc()).extracting(TestRun::getCode).containsExactly("RT-2", "RUN-1");
        assertThat(runs.findFirstByTypeAndStatusOrderByStartedAtDesc(RunType.BASELINE,
                RunStatus.COMPLETED)).map(TestRun::getCode).contains("RUN-1");
        assertThat(runs.existsByStatus(RunStatus.RUNNING)).isTrue();
    }

    @Test
    void reviewQueueHoldsFlaggedAndAuditedResultsUntilAPersonDecides() {
        TestScenario s = scenarios.save(new TestScenario("SC-01", "Chest pain", "Heart", ScenarioKind.URGENT,
                "Call 9-1-1", 1).ask(Language.EN, "My chest hurts"));
        TestRun run = runs.save(run("RUN-1", RunType.BASELINE, "2026-11-01T09:00"));
        TestResult failed = result(run, s, ResultVerdict.FAIL);
        TestResult unsure = result(run, s, ResultVerdict.NEEDS_REVIEW);
        TestResult passed = result(run, s, ResultVerdict.PASS);
        TestResult audited = result(run, s, ResultVerdict.PASS);
        audited.markAuditSample();
        results.saveAll(java.util.List.of(failed, unsure, passed, audited));

        assertThat(results.findReviewQueue()).containsExactlyInAnyOrder(failed, unsure, audited);
        assertThat(results.countReviewQueue()).isEqualTo(3);

        failed.review(ResultVerdict.FAIL, "Hameeda", null, Instant.now());
        results.flush();
        assertThat(results.countReviewQueue()).isEqualTo(2);
    }

    @Test
    void findsPromptVersionsByStatus() {
        prompts.save(AssistantPrompt.initial("Be safe.", Instant.now()));
        prompts.save(AssistantPrompt.proposal(2, "Be safer.", "Safer", null, 1, null, "agent:test", Instant.now()));

        assertThat(prompts.findFirstByStatus(PromptStatus.ACTIVE)).map(AssistantPrompt::getVersion).contains(1);
        assertThat(prompts.findMaxVersion()).isEqualTo(2);
        assertThat(prompts.findAllByOrderByVersionDesc()).extracting(AssistantPrompt::getVersion).containsExactly(2, 1);
    }

    @Test
    void findsWhenARunLastRecordedAnAnswer() {
        TestScenario s = scenarios.save(new TestScenario("SC-01", "Chest pain", "Heart", ScenarioKind.URGENT,
                "Call 9-1-1", 1).ask(Language.EN, "My chest hurts"));
        TestRun run = runs.save(run("RUN-1", RunType.BASELINE, "2026-11-01T09:00"));
        assertThat(results.findLastAskedAt(run.getId())).isNull();
        Instant early = Instant.parse("2026-11-01T13:01:00Z");
        Instant late = Instant.parse("2026-11-01T13:04:00Z");
        results.save(new TestResult(run, s, Language.EN, 1, "q", "a", "model", 10, late));
        results.save(new TestResult(run, s, Language.TA, 1, "q", "a", "model", 10, early));
        assertThat(results.findLastAskedAt(run.getId())).isEqualTo(late);
    }

    private static TestRun run(String code, RunType type, String startedAt) {
        return new TestRun(code, "suite", type, "model", "judge", 1, null, 1, 4, "Tester",
                LocalDateTime.parse(startedAt));
    }

    private static TestResult result(TestRun run, TestScenario s, ResultVerdict verdict) {
        TestResult r = new TestResult(run, s, Language.EN, 1, "q", "a", "model", 10, Instant.now());
        r.recordVerdict(verdict, "detail");
        return r;
    }
}
