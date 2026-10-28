package com.suwasarana.api.common;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Helpers for the two meanings of "now" (see {@code config/TimeConfig}). Both take the injected {@link Clock} so tests
 * can fix the time.
 */
public final class Times {

    private Times() {}

    /** A moment as a UTC {@link LocalDateTime}, for entity fields stored in UTC (createdAt, expiresAt ...). */
    public static LocalDateTime utcNow(Clock clock) {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    /** Wall-clock time in the clock's zone (Asia/Colombo): donor-visible times such as slot starts. */
    public static LocalDateTime colomboNow(Clock clock) {
        return LocalDateTime.now(clock);
    }
}
