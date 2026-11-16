package com.cognizant.backend.llm;

import java.time.Duration;

/**
 * Spaces out requests to stay under a provider's per-minute limit (e.g. 20 requests a minute for a
 * new account). Threads reserve the next free slot and wait for it, so bursts are smoothed, not dropped.
 */
final class RequestSpacer {

    private final long intervalNanos;
    private long nextSlot;

    /** @param perMinute requests allowed per minute; 0 or less means no spacing */
    RequestSpacer(int perMinute) {
        this.intervalNanos = perMinute > 0 ? Duration.ofMinutes(1).toNanos() / perMinute : 0;
    }

    /** Waits until this caller's slot. */
    void acquire() throws InterruptedException {
        if (intervalNanos == 0) {
            return;
        }
        long wait;
        synchronized (this) {
            long now = System.nanoTime();
            long slot = Math.max(now, nextSlot);
            nextSlot = slot + intervalNanos;
            wait = slot - now;
        }
        if (wait > 0) {
            Thread.sleep(Duration.ofNanos(wait));
        }
    }
}
