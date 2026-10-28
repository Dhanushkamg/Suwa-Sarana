package com.suwasarana.api.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class TimesTest {

    /** 2026-10-28 20:00 UTC == 2026-10-29 01:30 in Colombo (UTC+05:30): the date differs for 5.5 hours every day. */
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-28T20:00:00Z"), ZoneId.of("Asia/Colombo"));

    @Test
    @DisplayName("moments are UTC, wall-clock time is Asia/Colombo")
    void twoKindsOfNow() {
        assertThat(Times.utcNow(clock)).isEqualTo(LocalDateTime.of(2026, 10, 28, 20, 0));
        assertThat(Times.colomboNow(clock)).isEqualTo(LocalDateTime.of(2026, 10, 29, 1, 30));
    }

    @Test
    @DisplayName("'today' and quiet-hours time follow the Colombo wall clock, not the server's UTC")
    void businessDateAndTime() {
        assertThat(LocalDate.now(clock)).isEqualTo(LocalDate.of(2026, 10, 29));
        assertThat(LocalTime.now(clock)).isEqualTo(LocalTime.of(1, 30));
    }
}
