package com.suwasarana.api.demo;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * The demo-data seeder exists only in development: when the {@code dev} profile is active or
 * {@code app.demo-data.enabled=true}, and never when the {@code prod} profile is active.
 */
public class DemoDataCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Environment env = context.getEnvironment();
        if (env.acceptsProfiles(Profiles.of("prod"))) return false;
        return env.acceptsProfiles(Profiles.of("dev")) || env.getProperty("app.demo-data.enabled", Boolean.class, false);
    }
}
