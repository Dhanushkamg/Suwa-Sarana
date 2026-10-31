package com.suwasarana.api.common;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;

/**
 * THE helper for the two meanings of "now" (see {@code config/TimeConfig}); there is no other clock abstraction.
 *
 * <ul>
 *   <li><b>moments</b> (created / expires / closed / sent, anything compared with a DB TIMESTAMP[TZ]) are UTC:
 *       {@link #utcNow(Clock)}, or {@code Instant.now(clock)};</li>
 *   <li><b>wall-clock rules in Sri Lanka</b> (quiet hours, "today", slot / camp times, reminder windows) use the
 *       Asia/Colombo clock: {@link #colomboNow(Clock)}, {@link #today(Clock)}, {@link #time(Clock)}.</li>
 * </ul>
 *
 * The no-argument {@code LocalDateTime.now()} / {@code LocalDate.now()} / {@code LocalTime.now()} depend on the JVM zone
 * and are forbidden in main code ({@code ClockUsageTest}).
 */
public final class Times {

    private Times() {}

    /** A moment as a UTC {@link LocalDateTime}, for entity fields stored in UTC (createdAt, expiresAt ...). */
    public static LocalDateTime utcNow(Clock clock) {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    /**
     * UTC "now" for places that cannot receive a {@link Clock}: entity field initialisers ({@code createdAt = ...}) and
     * small static helpers. Independent of the JVM zone. Services that tests need to control take the {@code Clock}.
     */
    public static LocalDateTime utcNow() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    /** Wall-clock time in the clock's zone (Asia/Colombo): donor-visible times such as slot starts. */
    public static LocalDateTime colomboNow(Clock clock) {
        return LocalDateTime.now(clock);
    }

    /** Today's date in the clock's zone (Asia/Colombo). */
    public static LocalDate today(Clock clock) {
        return LocalDate.now(clock);
    }

    /** Time of day in the clock's zone (Asia/Colombo), e.g. for quiet hours. */
    public static LocalTime time(Clock clock) {
        return LocalTime.now(clock);
    }
}
