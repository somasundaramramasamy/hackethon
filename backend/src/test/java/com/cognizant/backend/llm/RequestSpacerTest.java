package com.cognizant.backend.llm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RequestSpacerTest {

    @Test
    void spacesBurstsToTheConfiguredRate() throws InterruptedException {
        RequestSpacer spacer = new RequestSpacer(600); // one every 100 ms
        long start = System.nanoTime();
        for (int i = 0; i < 4; i++) {
            spacer.acquire();
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertThat(elapsedMs).isBetween(280L, 1000L);
    }

    @Test
    void doesNotWaitWithoutALimit() throws InterruptedException {
        RequestSpacer spacer = new RequestSpacer(0);
        long start = System.nanoTime();
        for (int i = 0; i < 100; i++) {
            spacer.acquire();
        }
        assertThat((System.nanoTime() - start) / 1_000_000).isLessThan(100L);
    }
}
