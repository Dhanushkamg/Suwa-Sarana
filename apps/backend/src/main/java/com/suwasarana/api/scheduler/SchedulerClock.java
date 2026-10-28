package com.suwasarana.api.scheduler;

import org.springframework.beans.factory.ObjectProvider;

import java.time.Clock;
import java.time.ZoneId;

/**
 * All scheduling decisions use an injected {@link Clock}. The application-wide Clock bean (Asia/Colombo) is used when
 * the platform configuration provides one; until then, and in any context without it, this Sri Lanka clock is used.
 */
final class SchedulerClock {

    static final ZoneId COLOMBO = ZoneId.of("Asia/Colombo");

    private SchedulerClock() {
    }

    static Clock resolve(ObjectProvider<Clock> provider) {
        Clock injected = provider == null ? null : provider.getIfUnique();
        return injected != null ? injected : Clock.system(COLOMBO);
    }
}
