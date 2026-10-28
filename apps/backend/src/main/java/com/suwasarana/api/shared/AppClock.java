package com.suwasarana.api.shared;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * Single place the lifecycle code asks "what time is it". Uses the application's {@link Clock} bean when one
 * exists (Asia/Colombo, provided by the platform configuration) and otherwise falls back to a Colombo clock, so
 * that "today", quiet hours and expiry are Sri Lankan wall-clock time regardless of the server's zone.
 * Tests replace the clock with {@link #setClock(Clock)}.
 */
@Component
public class AppClock {

    public static final ZoneId COLOMBO = ZoneId.of("Asia/Colombo");

    private final ObjectProvider<Clock> provider;
    private volatile Clock override;

    public AppClock(ObjectProvider<Clock> provider) {
        this.provider = provider;
    }

    /** For plain unit tests that build services without Spring. */
    public static AppClock system() {
        return new AppClock(null);
    }

    public static AppClock fixed(Instant instant) {
        AppClock c = new AppClock(null);
        c.override = Clock.fixed(instant, COLOMBO);
        return c;
    }

    public void setClock(Clock clock) {
        this.override = clock;
    }

    public Clock clock() {
        Clock o = override;
        if (o != null) return o;
        Clock c = provider == null ? null : provider.getIfAvailable();
        return c != null ? c : Clock.system(COLOMBO);
    }

    public ZoneId zone() {
        return clock().getZone();
    }

    public Instant instant() {
        return clock().instant();
    }

    public LocalDateTime now() {
        return LocalDateTime.ofInstant(instant(), zone());
    }

    public LocalDate today() {
        return LocalDate.ofInstant(instant(), zone());
    }

    public LocalTime time() {
        return LocalTime.ofInstant(instant(), zone());
    }
}
