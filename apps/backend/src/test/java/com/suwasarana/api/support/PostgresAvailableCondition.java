package com.suwasarana.api.support;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Skips PostgreSQL-backed tests when no PostgreSQL can be provided - neither Docker (Testcontainers) nor an external
 * server configured with {@code -Dsuwa.test.pg.url} / {@code SUWA_TEST_PG_URL} - so a plain {@code mvn test} still works
 * on a laptop without either. CI sets {@code REQUIRE_POSTGRES=true} (the old name {@code REQUIRE_DOCKER=true} still
 * works): there the tests must run, and a missing database fails the build instead of silently skipping coverage of the
 * migrations.
 */
public class PostgresAvailableCondition implements ExecutionCondition {

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        if (SharedPostgres.isAvailable()) {
            return ConditionEvaluationResult.enabled("PostgreSQL available (" + SharedPostgres.mode() + ")");
        }
        if (isRequired()) {
            throw new IllegalStateException("REQUIRE_POSTGRES (or REQUIRE_DOCKER) is true but neither a Docker daemon nor "
                    + "an external PostgreSQL (-Dsuwa.test.pg.url / SUWA_TEST_PG_URL) is available");
        }
        return ConditionEvaluationResult.disabled("No Docker and no external PostgreSQL: skipping PostgreSQL integration test");
    }

    /** True when {@code REQUIRE_POSTGRES} or the legacy {@code REQUIRE_DOCKER} is {@code true}. */
    public static boolean isRequired() {
        return "true".equalsIgnoreCase(System.getenv("REQUIRE_POSTGRES"))
                || "true".equalsIgnoreCase(System.getenv("REQUIRE_DOCKER"));
    }
}
