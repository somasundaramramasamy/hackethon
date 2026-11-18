package com.cognizant.backend.run;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.cognizant.backend.entity.Language;
import com.cognizant.backend.entity.ResultVerdict;
import com.cognizant.backend.entity.TestResult;

class RunRecorderTest {

    private static final Instant NOW = Instant.parse("2026-11-16T06:00:00Z");

    @Test
    void aRunThatIsStillAnsweringIsNotStaleEvenIfAnotherBackendStarts() {
        assertThat(RunRecorder.isStale(NOW.minusSeconds(30), NOW)).isFalse();
        assertThat(RunRecorder.isStale(NOW.minus(RunRecorder.STALE_AFTER).plusSeconds(1), NOW)).isFalse();
    }

    @Test
    void aRunSilentForLongerThanTheLimitIsStale() {
        assertThat(RunRecorder.isStale(NOW.minus(RunRecorder.STALE_AFTER).minus(Duration.ofSeconds(1)), NOW)).isTrue();
    }

    @Test
    void spotChecksTwentyPercentOfFailuresAndTenPercentOfPassesInEachLanguage() {
        RunRecorder recorder = new RunRecorder(null, null, null, null, null, new Random(7));
        List<TestResult> all = new ArrayList<>();
        all.addAll(answers(Language.TA, ResultVerdict.FAIL, 10));
        all.addAll(answers(Language.TA, ResultVerdict.PASS, 20));
        all.addAll(answers(Language.HI, ResultVerdict.FAIL, 1));
        all.addAll(answers(Language.HI, ResultVerdict.NEEDS_REVIEW, 3));

        assertThat(recorder.sampleSpotChecks(all)).isEqualTo(2 + 2 + 1);
        assertThat(picked(all, Language.TA, ResultVerdict.FAIL)).isEqualTo(2);
        assertThat(picked(all, Language.TA, ResultVerdict.PASS)).isEqualTo(2);
        assertThat(picked(all, Language.HI, ResultVerdict.FAIL)).isEqualTo(1); // at least one
        assertThat(picked(all, Language.HI, ResultVerdict.NEEDS_REVIEW)).isZero(); // already queued

        // Calling it again only tops up, and a reviewed answer counts as checked.
        assertThat(recorder.sampleSpotChecks(all)).isZero();
        List<TestResult> reviewed = answers(Language.ML, ResultVerdict.FAIL, 3);
        reviewed.getFirst().review(ResultVerdict.FAIL, "Amala", null, NOW);
        assertThat(recorder.sampleSpotChecks(reviewed)).isZero();
    }

    private static List<TestResult> answers(Language language, ResultVerdict verdict, int n) {
        List<TestResult> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            TestResult r = new TestResult(null, null, language, 1, "q", "a", "model", 10, NOW);
            r.recordVerdict(verdict, "detail");
            list.add(r);
        }
        return list;
    }

    private static long picked(List<TestResult> all, Language language, ResultVerdict verdict) {
        return all.stream()
                .filter(r -> r.getLanguage() == language && r.getVerdict() == verdict && r.isAuditSample())
                .count();
    }
}
