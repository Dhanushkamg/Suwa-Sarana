package com.suwasarana.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Time handling (audit F-26).
 *
 * <ul>
 *   <li><b>Storage is UTC.</b> The JVM zone is pinned to UTC in {@code ApiApplication.main}, and Hibernate reads/writes
 *       timestamps with {@code hibernate.jdbc.time_zone=UTC}. Absolute moments should be {@code Instant}
 *       (or {@code TIMESTAMPTZ} columns).</li>
 *   <li><b>Business time is Asia/Colombo.</b> Anything a person experiences on the wall clock - donor quiet hours,
 *       "today" for eligibility, slot reminder windows, camp dates - must come from this {@link Clock}:
 *       {@code LocalDate.now(clock)}, {@code LocalTime.now(clock)}, {@code LocalDateTime.now(clock)}.
 *       Never call the no-argument {@code now()} methods in application code.</li>
 *   <li>Inject the {@code Clock} (constructor parameter) so tests can use {@code Clock.fixed(...)}.</li>
 * </ul>
 */
@Configuration
public class TimeConfig {

    @Bean
    public Clock clock(@Value("${app.time-zone:Asia/Colombo}") String zoneId) {
        return Clock.system(ZoneId.of(zoneId));
    }
}
