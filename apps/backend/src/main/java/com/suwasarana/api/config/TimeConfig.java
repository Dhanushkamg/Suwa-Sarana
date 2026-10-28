package com.suwasarana.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Time handling (audit F-26). Two kinds of "now" exist; mixing them is the bug this class prevents.
 *
 * <ol>
 *   <li><b>Moments</b> - when something happened or expires (createdAt, expiresAt, lastEscalatedAt, OTP expiry, refresh
 *       token expiry, retention cut-offs). Stored in UTC: the JVM zone is pinned to UTC in {@code ApiApplication.main},
 *       Hibernate uses {@code hibernate.jdbc.time_zone=UTC}, and most of these columns are {@code TIMESTAMPTZ}.
 *       Use {@code Instant.now(clock)}, or for an entity that still maps {@code LocalDateTime}:
 *       {@code Times.utcNow(clock)}.</li>
 *   <li><b>Wall-clock time as people read it in Sri Lanka</b> - donor quiet hours, "today" for eligibility and camp
 *       dates, slot start/end times typed by organisers (columns {@code donation_slots.start_time/end_time} are plain
 *       {@code TIMESTAMP} holding Colombo wall time), reminder windows. Use {@code LocalTime.now(clock)},
 *       {@code LocalDate.now(clock)}, {@code LocalDateTime.now(clock)} with this Asia/Colombo clock
 *       (or {@code Times.colomboNow(clock)}).</li>
 * </ol>
 *
 * <p>Never call the no-argument {@code now()} methods in application code, and inject the {@link Clock} (constructor
 * parameter) so tests can use {@code Clock.fixed(...)}.</p>
 */
@Configuration
public class TimeConfig {

    @Bean
    public Clock clock(@Value("${app.time-zone:Asia/Colombo}") String zoneId) {
        return Clock.system(ZoneId.of(zoneId));
    }
}
