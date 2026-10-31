package com.suwasarana.api.shared;

import com.suwasarana.api.common.Times;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * Convenience wrapper for the lifecycle code around THE application {@link Clock} bean ({@code config/TimeConfig},
 * Asia/Colombo). It owns no time logic of its own: every method delegates to {@link Times}. Use:
 * <ul>
 *   <li>{@link #utcNow()} for MOMENTS (created / expires / closed / matched ...: anything compared with DB timestamps);</li>
 *   <li>{@link #now()}, {@link #today()}, {@link #time()} for Sri Lankan WALL-CLOCK rules (slot times, quiet hours, "today").</li>
 * </ul>
 * Never compare the two kinds. Tests swap the clock with {@link #setClock(Clock)} or build {@link #fixed(Instant)}.
 */
@Component
public class AppClock {

    public static final ZoneId COLOMBO = ZoneId.of("Asia/Colombo");

    private final Clock base;
    private volatile Clock override;

    @Autowired
    public AppClock(Clock clock) {
        this.base = clock;
    }

    /** For plain unit tests that build services without Spring. */
    public static AppClock system() {
        return new AppClock(Clock.system(COLOMBO));
    }

    public static AppClock fixed(Instant instant) {
        return new AppClock(Clock.fixed(instant, COLOMBO));
    }

    /** Test hook: replace the clock; {@code null} restores the application clock. */
    public void setClock(Clock clock) {
        this.override = clock;
    }

    public Clock clock() {
        Clock o = override;
        return o != null ? o : base;
    }

    public ZoneId zone() {
        return clock().getZone();
    }

    public Instant instant() {
        return clock().instant();
    }

    /** Sri Lankan WALL-CLOCK time: for "today", slot start/end times, camp times and quiet hours only. */
    public LocalDateTime now() {
        return Times.colomboNow(clock());
    }

    /**
     * A MOMENT as a UTC wall-clock value: use for created/expires/closed/matched timestamps and anything compared with
     * them. Never compare it with {@link #now()}.
     */
    public LocalDateTime utcNow() {
        return Times.utcNow(clock());
    }

    public LocalDate today() {
        return Times.today(clock());
    }

    public LocalTime time() {
        return Times.time(clock());
    }
}
