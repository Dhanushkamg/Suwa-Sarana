package com.suwasarana.api.ai.llm;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Daily token budget (prompt + completion tokens) for one application instance, reset at midnight in the given zone.
 * In-memory: with several instances each has its own budget, so set the per-instance figure accordingly.
 */
public final class TokenBudget {

    private final long daily;
    private final Clock clock;
    private final ZoneId zone;
    private LocalDate day;
    private long used;

    public TokenBudget(long dailyTokens, Clock clock, ZoneId zone) {
        this.daily = dailyTokens;
        this.clock = clock;
        this.zone = zone;
    }

    /** Reserves an estimate before a call; false when the day's budget would be exceeded. 0 = unlimited. */
    public synchronized boolean tryReserve(long estimate) {
        rollover();
        if (daily <= 0) {
            used += estimate;
            return true;
        }
        if (used + estimate > daily) return false;
        used += estimate;
        return true;
    }

    /** Replaces the reserved estimate by the real usage (0 when the call failed before using tokens). */
    public synchronized void settle(long estimate, long actual) {
        rollover();
        used = Math.max(0, used - estimate + actual);
    }

    public synchronized long used() {
        rollover();
        return used;
    }

    public long daily() {
        return daily;
    }

    private void rollover() {
        LocalDate today = LocalDate.now(clock.withZone(zone));
        if (!today.equals(day)) {
            day = today;
            used = 0;
        }
    }
}
