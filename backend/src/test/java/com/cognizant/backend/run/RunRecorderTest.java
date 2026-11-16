package com.cognizant.backend.run;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

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
}
