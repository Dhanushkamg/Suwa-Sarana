package com.suwasarana.api.config;

import com.suwasarana.api.ApiApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.mock.env.MockEnvironment;

import java.util.Base64;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionSafetyCheckTest {

    /** The secret that used to be the application.yml default and is in git history. */
    private static final String OLD_COMMITTED_SECRET = "dGhpcy1pcy1hLXZlcnktbG9uZy1zZWNyZXQta2V5LXRvLW1lZXQtam1hYy1yZXF1aXJlbWVudHM=";

    private static String randomSecret(int bytes) {
        byte[] b = new byte[bytes];
        new Random(42).nextBytes(b);
        return Base64.getEncoder().encodeToString(b);
    }

    /** An environment that satisfies every check. */
    private static MockEnvironment validProd() {
        return new MockEnvironment()
                .withProperty("jwt.secret", randomSecret(64))
                .withProperty("spring.datasource.url", "jdbc:postgresql://db.internal:5432/suwa")
                .withProperty("spring.datasource.username", "suwa_app")
                .withProperty("spring.datasource.password", "Zq8-generated-pass-4417")
                .withProperty("cors.allowed-origin", "https://suwasarana.lk,https://www.suwasarana.lk")
                .withProperty("app.frontend-url", "https://suwasarana.lk")
                .withProperty("app.public-base-url", "https://api.suwasarana.lk")
                .withProperty("app.cookie.secure", "true")
                .withProperty("app.seed-data.enabled", "false")
                .withProperty("spring.flyway.placeholders.environment", "prod");
    }

    @Test
    @DisplayName("a complete production configuration passes")
    void validConfigurationPasses() {
        assertThat(ProductionSafetyCheck.validate(validProd())).isEmpty();
    }

    @Test
    @DisplayName("a missing JWT secret is rejected")
    void missingJwtSecret() {
        MockEnvironment env = validProd().withProperty("jwt.secret", "");
        assertThat(ProductionSafetyCheck.validate(env)).anyMatch(p -> p.contains("JWT_SECRET is missing"));
    }

    @Test
    @DisplayName("the previously committed JWT secret is rejected")
    void committedJwtSecret() {
        MockEnvironment env = validProd().withProperty("jwt.secret", OLD_COMMITTED_SECRET);
        assertThat(ProductionSafetyCheck.validate(env)).anyMatch(p -> p.contains("committed to the repository"));
    }

    @Test
    @DisplayName("short, non-base64 and low-entropy JWT secrets are rejected")
    void weakJwtSecrets() {
        assertThat(ProductionSafetyCheck.validate(validProd().withProperty("jwt.secret", randomSecret(16))))
                .anyMatch(p -> p.contains("too short"));
        assertThat(ProductionSafetyCheck.validate(validProd().withProperty("jwt.secret", "CHANGE_ME_BEFORE_PRODUCTION")))
                .anyMatch(p -> p.contains("Base64"));
        assertThat(ProductionSafetyCheck.validate(validProd().withProperty("jwt.secret",
                Base64.getEncoder().encodeToString("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa".getBytes()))))
                .anyMatch(p -> p.contains("entropy"));
    }

    @Test
    @DisplayName("missing or weak database credentials are rejected")
    void databaseCredentials() {
        MockEnvironment env = validProd()
                .withProperty("spring.datasource.url", "")
                .withProperty("spring.datasource.password", "postgres123");
        List<String> problems = ProductionSafetyCheck.validate(env);
        assertThat(problems).anyMatch(p -> p.contains("DB_URL"));
        assertThat(problems).anyMatch(p -> p.contains("DB_PASSWORD"));
    }

    @Test
    @DisplayName("URLs, CORS, cookies, seed data and Flyway dev settings are validated")
    void otherSettings() {
        MockEnvironment env = validProd()
                .withProperty("app.frontend-url", "")
                .withProperty("app.public-base-url", "http://api.suwasarana.lk")
                .withProperty("cors.allowed-origin", "http://suwasarana.lk")
                .withProperty("app.cookie.secure", "false")
                .withProperty("app.seed-data.enabled", "true")
                .withProperty("spring.flyway.placeholders.environment", "dev")
                .withProperty("spring.flyway.out-of-order", "true");
        List<String> problems = ProductionSafetyCheck.validate(env);
        assertThat(problems).anyMatch(p -> p.contains("FRONTEND_URL"));
        assertThat(problems).anyMatch(p -> p.contains("PUBLIC_BASE_URL") && p.contains("https"));
        assertThat(problems).anyMatch(p -> p.contains("CORS origin"));
        assertThat(problems).anyMatch(p -> p.contains("COOKIE_SECURE"));
        assertThat(problems).anyMatch(p -> p.contains("SEED_DATA_ENABLED"));
        assertThat(problems).anyMatch(p -> p.contains("APP_ENV"));
        assertThat(problems).anyMatch(p -> p.contains("out-of-order"));
    }

    @Test
    @DisplayName("only the dev and test profiles are relaxed - no profile means strict")
    void relaxedProfiles() {
        assertThat(ProductionSafetyCheck.isRelaxed(new MockEnvironment().withProperty("x", "y"))).isFalse();
        MockEnvironment dev = new MockEnvironment();
        dev.setActiveProfiles("dev");
        MockEnvironment test = new MockEnvironment();
        test.setActiveProfiles("test");
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");
        MockEnvironment staging = new MockEnvironment();
        staging.setActiveProfiles("staging");
        assertThat(ProductionSafetyCheck.isRelaxed(dev)).isTrue();
        assertThat(ProductionSafetyCheck.isRelaxed(test)).isTrue();
        assertThat(ProductionSafetyCheck.isRelaxed(prod)).isFalse();
        assertThat(ProductionSafetyCheck.isRelaxed(staging)).isFalse();
    }

    @Test
    @DisplayName("the application context refuses to start under prod without a JWT secret, before touching the database")
    void startupFailsFastWithoutSecret() {
        SpringApplication app = new SpringApplication(ApiApplication.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        assertThatThrownBy(() -> app.run("--spring.profiles.active=prod", "--jwt.secret=", "--spring.main.banner-mode=off"))
                .hasStackTraceContaining("Refusing to start")
                .hasStackTraceContaining("JWT_SECRET is missing");
    }

    @Test
    @DisplayName("the application context refuses to start under prod with the old committed secret")
    void startupFailsFastWithKnownSecret() {
        SpringApplication app = new SpringApplication(ApiApplication.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        assertThatThrownBy(() -> app.run("--spring.profiles.active=prod", "--jwt.secret=" + OLD_COMMITTED_SECRET,
                "--spring.main.banner-mode=off"))
                .hasStackTraceContaining("committed to the repository");
    }

    @Test
    @DisplayName("running with no profile at all is also strict")
    void startupFailsFastWithoutProfile() {
        SpringApplication app = new SpringApplication(ApiApplication.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        assertThatThrownBy(() -> app.run("--spring.main.banner-mode=off"))
                .hasStackTraceContaining("Refusing to start");
    }
}
