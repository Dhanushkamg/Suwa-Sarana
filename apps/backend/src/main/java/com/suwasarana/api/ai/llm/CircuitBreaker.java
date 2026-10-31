package com.suwasarana.api.ai.llm;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Consecutive-failure circuit breaker. After {@code threshold} failures in a row the circuit opens for
 * {@code openFor}; after that one probe call is let through (half-open): success closes the circuit, failure re-opens it.
 */
public final class CircuitBreaker {

    public enum State { CLOSED, OPEN, HALF_OPEN }

    private final int threshold;
    private final Duration openFor;
    private final Clock clock;
    private int failures;
    private Instant openUntil;
    private boolean probeInFlight;

    public CircuitBreaker(int threshold, Duration openFor, Clock clock) {
        this.threshold = Math.max(1, threshold);
        this.openFor = openFor;
        this.clock = clock;
    }

    /** Whether a call may proceed right now. */
    public synchronized boolean allow() {
        if (openUntil == null) return true;
        if (clock.instant().isBefore(openUntil)) return false;
        if (probeInFlight) return false;
        probeInFlight = true;
        return true;
    }

    public synchronized void onSuccess() {
        failures = 0;
        openUntil = null;
        probeInFlight = false;
    }

    public synchronized void onFailure() {
        probeInFlight = false;
        failures++;
        if (failures >= threshold) {
            openUntil = clock.instant().plus(openFor);
        }
    }

    public synchronized State state() {
        if (openUntil == null) return State.CLOSED;
        return clock.instant().isBefore(openUntil) ? State.OPEN : State.HALF_OPEN;
    }
}
