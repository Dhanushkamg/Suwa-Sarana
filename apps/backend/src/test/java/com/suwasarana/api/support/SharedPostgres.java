package com.suwasarana.api.support;

import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * One PostgreSQL per test JVM, shared by every {@link PostgresIntegrationTest}.
 *
 * <p>Default: a Testcontainers {@code postgres:16-alpine} (ships the {@code cube} and {@code earthdistance} contrib
 * extensions the matching query needs). Started lazily on first use and removed by Testcontainers' Ryuk when the JVM
 * exits.</p>
 *
 * <p>Escape hatch for machines where pulling images is slow: point the tests at an existing server with
 * {@code PGTEST_JDBC_URL}, {@code PGTEST_USER}, {@code PGTEST_PASSWORD}. The database name MUST end with
 * {@code _pgtest} (its {@code public} schema is dropped and recreated before the run), so a development database can
 * never be wiped by accident.</p>
 */
public final class SharedPostgres {

    private static final String IMAGE = "postgres:16-alpine";

    private static volatile PostgreSQLContainer container;
    private static volatile Boolean available;

    private SharedPostgres() {}

    /**
     * True when a database can be provided (external URL configured or Docker reachable).
     * {@code PGTEST_DISABLE=true} pretends Docker is absent (to exercise the skip / REQUIRE_DOCKER paths).
     */
    public static synchronized boolean isAvailable() {
        if (available == null) {
            available = !"true".equalsIgnoreCase(System.getenv("PGTEST_DISABLE"))
                    && (externalUrl() != null || dockerReachable());
        }
        return available;
    }

    public static String jdbcUrl() {
        String external = externalUrl();
        if (external != null) {
            prepareExternalOnce();
            return external;
        }
        return container().getJdbcUrl();
    }

    public static String username() {
        return externalUrl() != null ? System.getenv().getOrDefault("PGTEST_USER", "postgres") : container().getUsername();
    }

    public static String password() {
        return externalUrl() != null ? System.getenv().getOrDefault("PGTEST_PASSWORD", "") : container().getPassword();
    }

    private static String externalUrl() {
        String url = System.getenv("PGTEST_JDBC_URL");
        return url == null || url.isBlank() ? null : url;
    }

    private static boolean dockerReachable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            return false;
        }
    }

    private static synchronized PostgreSQLContainer container() {
        if (container == null) {
            PostgreSQLContainer c = new PostgreSQLContainer(IMAGE)
                    .withDatabaseName("suwa_pgtest")
                    .withUsername("suwa_test")
                    // not a known-weak value: the strict (non-dev) start-up check also runs against this profile
                    .withPassword("pgtest-Zr4k9Qw2Lm7x");
            c.start();
            container = c;
        } else if (!container.isRunning()) {
            throw new IllegalStateException("Shared PostgreSQL container stopped unexpectedly");
        }
        return container;
    }

    /**
     * Creates (or recreates) an empty extra database next to the shared one and returns its JDBC URL, for tests that
     * drive Flyway themselves. The name must end with {@code _pgtest}.
     */
    public static String createDatabase(String name) throws SQLException {
        if (!name.matches("[a-z0-9_]+_pgtest")) {
            throw new IllegalArgumentException("database name must match [a-z0-9_]+_pgtest: " + name);
        }
        String base = jdbcUrl();
        String root = base.substring(0, base.lastIndexOf('/') + 1);
        String query = base.contains("?") ? base.substring(base.indexOf('?')) : "";
        try (Connection c = DriverManager.getConnection(base, username(), password()); Statement st = c.createStatement()) {
            st.execute("DROP DATABASE IF EXISTS " + name + " WITH (FORCE)");
            st.execute("CREATE DATABASE " + name);
        }
        return root + name + query;
    }

    private static boolean externalPrepared;

    /** Wipes the external database once per JVM, before the first Spring context uses it. */
    private static synchronized void prepareExternalOnce() {
        if (externalPrepared) {
            return;
        }
        try {
            resetExternalDatabase();
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot reset external test database", e);
        }
        externalPrepared = true;
    }

    private static void resetExternalDatabase() throws SQLException {
        String url = externalUrl();
        String db = url.substring(url.lastIndexOf('/') + 1);
        int q = db.indexOf('?');
        if (q >= 0) {
            db = db.substring(0, q);
        }
        if (!db.endsWith("_pgtest")) {
            throw new IllegalStateException("Refusing to reset database '" + db + "': PGTEST_JDBC_URL must point to a database whose name ends with _pgtest");
        }
        try (Connection c = DriverManager.getConnection(url, username(), password()); Statement s = c.createStatement()) {
            s.execute("DROP SCHEMA public CASCADE");
            s.execute("CREATE SCHEMA public");
        }
    }
}
