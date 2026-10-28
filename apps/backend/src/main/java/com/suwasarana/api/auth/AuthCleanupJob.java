package com.suwasarana.api.auth;

import com.suwasarana.api.auth.token.AuthTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/** Daily housekeeping of the auth tables: expired refresh tokens (including the rotated ones kept for reuse detection) and old one-time tokens. */
@Component
public class AuthCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(AuthCleanupJob.class);

    private final RefreshTokenRepository refreshTokenRepository;
    private final AuthTokenRepository authTokenRepository;

    public AuthCleanupJob(RefreshTokenRepository refreshTokenRepository, AuthTokenRepository authTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.authTokenRepository = authTokenRepository;
    }

    @Scheduled(cron = "0 30 3 * * *")
    public void purge() {
        Instant now = Instant.now();
        int refresh = refreshTokenRepository.deleteByExpiresAtBefore(now);
        int tokens = authTokenRepository.deleteByExpiresAtBefore(now.minus(Duration.ofDays(7)));
        log.info("Auth cleanup: {} expired refresh tokens, {} old one-time tokens removed", refresh, tokens);
    }
}
