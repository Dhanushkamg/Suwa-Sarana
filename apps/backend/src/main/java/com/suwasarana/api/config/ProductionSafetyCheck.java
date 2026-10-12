package com.suwasarana.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Refuses to start under the "prod" profile with the development defaults that ship in application.yml.
 */
@Component
@Profile("prod")
public class ProductionSafetyCheck {

    private static final String DEV_JWT_SECRET =
            "dGhpcy1pcy1hLXZlcnktbG9uZy1zZWNyZXQta2V5LXRvLW1lZXQtam1hYy1yZXF1aXJlbWVudHM=";

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${app.seed-data.enabled:false}")
    private boolean seedData;

    @EventListener(ApplicationReadyEvent.class)
    public void verify() {
        if (DEV_JWT_SECRET.equals(jwtSecret)) {
            throw new IllegalStateException("JWT_SECRET must be set to a private value in production.");
        }
        if (seedData) {
            throw new IllegalStateException("SEED_DATA_ENABLED must be false in production.");
        }
    }
}
