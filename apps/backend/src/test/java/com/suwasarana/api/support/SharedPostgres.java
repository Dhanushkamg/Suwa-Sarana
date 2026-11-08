package com.suwasarana.api.support;

import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One PostgreSQL per test JVM, shared by every {@link PostgresIntegrationTest}, plus scratch databases for tests that
 * drive Flyway themselves.
 *
 * <h3>Where the PostgreSQL comes from</h3>
 * <ol>
 *   <li><b>Docker (default when available)</b>: a Testcontainers {@code postgres:16-alpine} (ships the {@code cube} and
 *       {@code earthdistance} contrib extensions the matching query needs), started lazily and removed by Ryuk.</li>
 *   <li><b>External server (fallback when Docker is not reachable)</b>: set
 *       {@code -Dsuwa.test.pg.url=jdbc:postgresql://127.0.0.1:55432/postgres} or the environment variable
 *       {@code SUWA_TEST_PG_URL}; credentials via {@code suwa.test.pg.user} / {@code suwa.test.pg.password}
 *       ({@code SUWA_TEST_PG_USER} / {@code SUWA_TEST_PG_PASSWORD}; default user {@code postgres} and a placeholder password, which
 *       suits a trust-auth throw-away cluster - the strict start-up check of the pgtest profile refuses an empty DB_PASSWORD). The role needs CREATEDB and permission to create the {@code cube} /
 *       {@code earthdistance} extensions (superuser). The URL's own database is only used for administration; every JVM
 *       creates its OWN scratch database {@code suwa_t_<random>} (and further ones for {@link #createDatabase}) and drops
 *       them on exit, so nothing already on that server is ever modified.
 *       {@code suwa.test.pg.prefer-external=true} uses the external server even when Docker is available.</li>
 * </ol>
 * {@code PGTEST_DISABLE=true} pretends that no database is available (to exercise the skip / REQUIRE_POSTGRES paths).
 * The legacy variables {@code PGTEST_JDBC_URL} / {@code PGTEST_USER} / {@code PGTEST_PASSWORD} are read as aliases of
 * the new settings (they no longer reset a schema in place).
 */
public final class SharedPostgres {

    private static final String IMAGE = "postgres:16-alpine";
    /** ignored by a trust-auth server, but ProductionSafetyCheck (active in the pgtest profile) rejects an empty password */
    private static final String TRUST_AUTH_PLACEHOLDER_PASSWORD = "pgtest-Zr4k9Qw2Lm7x";

    private static volatile PostgreSQLContainer container;
    private static Boolean available;
    private static Boolean useExternal;

    /** logical name -> real database name (external mode adds a per-JVM token) */
    private static final Map<String, String> CREATED = new ConcurrentHashMap<>();
    private static final String JVM_TOKEN = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    private static String sharedExternalDb;
    private static boolean hookRegistered;

    private SharedPostgres() {}

    /** True when a database can be provided (Docker reachable or external URL configured). */
    public static synchronized boolean isAvailable() {
        if (available == null) {
            available = !"true".equalsIgnoreCase(System.getenv("PGTEST_DISABLE")) && (resolveExternal() || dockerReachable());
        }
        return available;
    }

    /** Human readable description of where the database comes from. */
    public static synchronized String mode() {
        return resolveExternal() ? "external PostgreSQL " + externalUrl() : "Testcontainers " + IMAGE;
    }

    /** True when tests talk to the external server (Docker absent, or {@code suwa.test.pg.prefer-external=true}). */
    private static synchronized boolean resolveExternal() {
        if (useExternal == null) {
            boolean configured = externalUrl() != null;
            boolean prefer = "true".equalsIgnoreCase(setting("suwa.test.pg.prefer-external", "SUWA_TEST_PG_PREFER_EXTERNAL", null));
            useExternal = configured && (prefer || !dockerReachable());
        }
        return useExternal;
    }

    /** JDBC URL of the database the shared Spring context uses (a fresh scratch database per JVM in external mode). */
    public static synchronized String jdbcUrl() {
        if (resolveExternal()) {
            if (sharedExternalDb == null) {
                String db = "suwa_t_" + JVM_TOKEN;
                createExternalDatabase(db);
                sharedExternalDb = db;
            }
            return urlFor(sharedExternalDb);
        }
        return container().getJdbcUrl();
    }

    public static String username() {
        return resolveExternal() ? setting("suwa.test.pg.user", "SUWA_TEST_PG_USER", "postgres") : container().getUsername();
    }

    public static String password() {
        return resolveExternal() ? setting("suwa.test.pg.password", "SUWA_TEST_PG_PASSWORD", TRUST_AUTH_PLACEHOLDER_PASSWORD) : container().getPassword();
    }

    /**
     * Creates (or recreates) an empty extra database for tests that drive Flyway themselves and returns its JDBC URL. The
     * logical name must match {@code [a-z0-9_]+}; use {@link #databaseUrl} to reach it again. Dropped on JVM exit in
     * external mode (removed with the container otherwise); {@link #dropDatabase} drops it earlier.
     */
    public static synchronized String createDatabase(String name) throws SQLException {
        if (!name.matches("[a-z0-9_]+")) {
            throw new IllegalArgumentException("database name must match [a-z0-9_]+: " + name);
        }
        String real = resolveExternal() ? "suwa_t_" + name + "_" + JVM_TOKEN : name;
        CREATED.put(name, real);
        registerHook();
        try (Connection c = adminConnection(); Statement st = c.createStatement()) {
            st.execute("DROP DATABASE IF EXISTS " + real + " WITH (FORCE)");
            st.execute("CREATE DATABASE " + real);
        }
        enableGeoExtensions(real);
        return urlFor(real);
    }

    /** JDBC URL of a database made by {@link #createDatabase}. */
    public static String databaseUrl(String name) {
        String real = CREATED.get(name);
        if (real == null) {
            throw new IllegalStateException("database not created: " + name);
        }
        return urlFor(real);
    }

    public static synchronized void dropDatabase(String name) {
        String real = CREATED.remove(name);
        if (real == null) {
            return;
        }
        try (Connection c = adminConnection(); Statement st = c.createStatement()) {
            st.execute("DROP DATABASE IF EXISTS " + real + " WITH (FORCE)");
        } catch (SQLException e) {
            System.err.println("Could not drop scratch database " + real + ": " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------------------------------ internals

    private static String externalUrl() {
        String url = setting("suwa.test.pg.url", "SUWA_TEST_PG_URL", null);
        return url == null || url.isBlank() ? null : url;
    }

    private static String setting(String property, String env, String fallback) {
        String v = System.getProperty(property);
        if (v == null || v.isBlank()) {
            v = System.getenv(env);
        }
        if ((v == null || v.isBlank()) && env.startsWith("SUWA_TEST_PG_")) {
            v = System.getenv("PGTEST_" + (env.equals("SUWA_TEST_PG_URL") ? "JDBC_URL" : env.substring("SUWA_TEST_PG_".length())));
        }
        return v == null || v.isBlank() ? fallback : v;
    }

    /** The configured URL's own database is used for administration only (usually {@code postgres}). */
    private static Connection adminConnection() throws SQLException {
        if (resolveExternal()) {
            return DriverManager.getConnection(externalUrl(), username(), password());
        }
        return DriverManager.getConnection(container().getJdbcUrl(), container().getUsername(), container().getPassword());
    }

    /** Replaces the database name in the base URL (external URL or the container's) with {@code db}. */
    private static String urlFor(String db) {
        String base = resolveExternal() ? externalUrl() : container().getJdbcUrl();
        int slash = base.lastIndexOf('/');
        int q = base.indexOf('?', slash);
        return base.substring(0, slash + 1) + db + (q >= 0 ? base.substring(q) : "");
    }

    private static void createExternalDatabase(String db) {
        registerHook();
        CREATED.put("shared", db);
        try (Connection c = adminConnection(); Statement st = c.createStatement()) {
            st.execute("CREATE DATABASE " + db);
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot create scratch database on " + externalUrl() + ": " + e.getMessage(), e);
        }
        try {
            enableGeoExtensions(db);
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot enable cube/earthdistance in " + db + " (superuser needed): " + e.getMessage(), e);
        }
    }

    /** V1 creates the extensions too; doing it up front gives a clear error if the server lacks the contrib module. */
    private static void enableGeoExtensions(String db) throws SQLException {
        try (Connection c = DriverManager.getConnection(urlFor(db), username(), password()); Statement st = c.createStatement()) {
            st.execute("CREATE EXTENSION IF NOT EXISTS cube");
            st.execute("CREATE EXTENSION IF NOT EXISTS earthdistance");
        }
    }

    private static synchronized void registerHook() {
        if (hookRegistered || !resolveExternal()) {
            return;
        }
        hookRegistered = true;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            for (String logical : CREATED.keySet().toArray(new String[0])) {
                dropDatabase(logical);
            }
        }, "suwa-pg-scratch-cleanup"));
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
}
