package com.suwasarana.api.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Fail-fast configuration guard (audit F-01 / F-31).
 *
 * <p>The application refuses to start - before any bean such as the DataSource or the JWT provider is created -
 * unless it runs under the explicit {@code dev} or {@code test} profile OR every security-relevant setting is
 * acceptable: strong non-default JWT secret, real DB credentials, explicit CORS origins, https URLs, no demo seed.
 * There is deliberately no lenient "default profile": forgetting {@code SPRING_PROFILES_ACTIVE} is a start-up error,
 * not an insecure deployment.</p>
 *
 * <p>Implemented as a {@link BeanFactoryPostProcessor} so it runs before regular singletons are instantiated.
 * Messages never contain secret values.</p>
 */
@Component
public class ProductionSafetyCheck implements BeanFactoryPostProcessor, EnvironmentAware {

    private static final Logger log = LoggerFactory.getLogger(ProductionSafetyCheck.class);

    /** Profiles allowed to run with development conveniences. */
    public static final String[] RELAXED_PROFILES = {"dev", "test"};

    /** JWT secrets that were ever committed to the repository or documentation. Rotate, never reuse. */
    private static final Set<String> KNOWN_JWT_SECRETS = Set.of(
            "dGhpcy1pcy1hLXZlcnktbG9uZy1zZWNyZXQta2V5LXRvLW1lZXQtam1hYy1yZXF1aXJlbWVudHM=",
            "ZGV2LW9ubHktdGhyb3ctYXdheS1qd3Qtc2lnbmluZy1rZXktbm90LWZvci1hbnl0aGluZy1yZWFsLTAxMjM0NTY3ODlhYmNkZWY=");

    private static final Set<String> WEAK_DB_PASSWORDS = Set.of(
            "postgres", "postgres123", "password", "password123", "devonly123", "devonly-postgres",
            "admin", "root", "123456", "changeme", "change_me", "secret");

    static final int MIN_JWT_SECRET_BYTES = 32;

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        // Wildcard + credentials is invalid in every profile.
        CorsConfig.parseOrigins(environment.getProperty("cors.allowed-origin", ""));
        if (isRelaxed(environment)) {
            log.warn("Running with a development/test profile: throw-away defaults and demo data are allowed. "
                    + "Never use this profile for a deployment that holds real data.");
            return;
        }
        List<String> problems = validate(environment);
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Refusing to start: unsafe configuration for a non-dev profile.\n - "
                    + String.join("\n - ", problems)
                    + "\nSee apps/backend/.env.example for every variable. Use SPRING_PROFILES_ACTIVE=dev for local development.");
        }
    }

    public static boolean isRelaxed(Environment env) {
        return env.acceptsProfiles(Profiles.of(RELAXED_PROFILES));
    }

    /** @return human-readable problems (empty when the configuration is acceptable). Never includes secret values. */
    public static List<String> validate(Environment env) {
        List<String> problems = new ArrayList<>();

        validateJwtSecret(env.getProperty("jwt.secret", ""), problems);

        String dbUrl = env.getProperty("spring.datasource.url", "");
        if (dbUrl.isBlank() || !dbUrl.startsWith("jdbc:")) {
            problems.add("DB_URL must be set to a JDBC URL (jdbc:postgresql://host:5432/db).");
        }
        if (env.getProperty("spring.datasource.username", "").isBlank()) {
            problems.add("DB_USER must be set.");
        }
        String dbPassword = env.getProperty("spring.datasource.password", "");
        if (dbPassword.isBlank()) {
            problems.add("DB_PASSWORD must be set.");
        } else if (WEAK_DB_PASSWORDS.contains(dbPassword.toLowerCase(Locale.ROOT)) || dbPassword.length() < 12) {
            problems.add("DB_PASSWORD is a known/weak value or shorter than 12 characters; use a generated password.");
        }

        String cors = env.getProperty("cors.allowed-origin", "");
        if (cors.isBlank()) {
            problems.add("CORS_ALLOWED_ORIGIN must list the frontend origin(s), e.g. https://suwasarana.lk.");
        } else {
            try {
                for (String origin : CorsConfig.parseOrigins(cors)) {
                    if (!origin.startsWith("https://") && !isLocalhost(origin)) {
                        problems.add("CORS origin '" + origin + "' must use https.");
                    }
                }
            } catch (IllegalStateException e) {
                problems.add(e.getMessage());
            }
        }

        requireHttpsUrl(env, "app.frontend-url", "FRONTEND_URL", problems);
        requireHttpsUrl(env, "app.public-base-url", "PUBLIC_BASE_URL", problems);

        if (env.getProperty("app.seed-data.enabled", Boolean.class, false)) {
            problems.add("SEED_DATA_ENABLED must be false: demo accounts use a publicly known password.");
        }
        if ("dev".equalsIgnoreCase(env.getProperty("spring.flyway.placeholders.environment", ""))) {
            problems.add("APP_ENV must not be 'dev' outside the dev profile (it keeps the seeded demo accounts, see V22).");
        }
        if (env.getProperty("spring.flyway.baseline-on-migrate", Boolean.class, false)
                || env.getProperty("spring.flyway.out-of-order", Boolean.class, false)) {
            problems.add("Flyway baseline-on-migrate / out-of-order must be off outside the dev profile.");
        }
        if (!env.getProperty("app.cookie.secure", Boolean.class, true)) {
            problems.add("COOKIE_SECURE must be true (the refresh-token cookie must only travel over HTTPS).");
        }

        if (env.getProperty("app.google.client-id", "").isBlank()) {
            log.warn("GOOGLE_CLIENT_ID is not set: Google sign-in will be unavailable.");
        }
        return problems;
    }

    private static void validateJwtSecret(String secret, List<String> problems) {
        if (secret == null || secret.isBlank()) {
            problems.add("JWT_SECRET is missing. Generate one with: openssl rand -base64 64");
            return;
        }
        String trimmed = secret.trim();
        if (KNOWN_JWT_SECRETS.contains(trimmed)) {
            problems.add("JWT_SECRET equals a secret that was committed to the repository; generate and rotate a new one.");
            return;
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(trimmed);
        } catch (IllegalArgumentException e) {
            problems.add("JWT_SECRET must be standard Base64 (openssl rand -base64 64).");
            return;
        }
        if (decoded.length < MIN_JWT_SECRET_BYTES) {
            problems.add("JWT_SECRET is too short: need at least " + MIN_JWT_SECRET_BYTES + " random bytes (64 recommended).");
        }
        long distinct = new String(decoded, StandardCharsets.ISO_8859_1).chars().distinct().count();
        if (distinct < 12) {
            problems.add("JWT_SECRET has too little entropy (repeated characters); generate it with openssl.");
        }
    }

    private static void requireHttpsUrl(Environment env, String property, String envVar, List<String> problems) {
        String value = env.getProperty(property, "");
        if (value.isBlank()) {
            problems.add(envVar + " must be set to the public https URL (no trailing slash).");
            return;
        }
        try {
            URI uri = URI.create(value);
            boolean ok = uri.getHost() != null
                    && ("https".equals(uri.getScheme()) || ("http".equals(uri.getScheme()) && isLocalhost(value)));
            if (!ok) {
                problems.add(envVar + " must be an absolute https URL.");
            }
            if (value.endsWith("/")) {
                problems.add(envVar + " must not end with '/'.");
            }
        } catch (IllegalArgumentException e) {
            problems.add(envVar + " is not a valid URL.");
        }
    }

    private static boolean isLocalhost(String url) {
        try {
            String host = URI.create(url).getHost();
            return "localhost".equals(host) || "127.0.0.1".equals(host) || "[::1]".equals(host);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
