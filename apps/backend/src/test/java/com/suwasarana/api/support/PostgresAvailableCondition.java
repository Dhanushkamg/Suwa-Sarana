package com.suwasarana.api.support;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Skips PostgreSQL-backed tests when neither Docker nor an external test database is available, so a plain
 * {@code mvn test} still works on a laptop without Docker. CI sets {@code REQUIRE_DOCKER=true}: there the tests must
 * run, and a missing Docker daemon fails the build instead of silently skipping coverage of the migrations.
 */
public class DockerAvailableCondition implements ExecutionCondition {

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        if (SharedPostgres.isAvailable()) {
            return ConditionEvaluationResult.enabled("PostgreSQL available");
        }
        if ("true".equalsIgnoreCase(System.getenv("REQUIRE_DOCKER"))) {
            throw new IllegalStateException("REQUIRE_DOCKER=true but no Docker daemon (or PGTEST_JDBC_URL) is available");
        }
        return ConditionEvaluationResult.disabled("Docker is not available: skipping PostgreSQL integration test");
    }
}
