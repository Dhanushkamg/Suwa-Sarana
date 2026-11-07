package com.suwasarana.api.integration;

import com.suwasarana.api.support.PostgresAvailableCondition;
import com.suwasarana.api.support.SharedPostgres;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Upgrade of an EXISTING database that already runs V1-V21 and holds data written by the old application, to the latest
 * migration, on a real PostgreSQL: phone numbers are normalised to E.164 (V25), circle invite tokens are hashed (V40),
 * nothing else is lost - plus the fresh-install path (empty database -> latest) in both Flyway placeholder modes.
 */
@ExtendWith(PostgresAvailableCondition.class)
class UpgradeFromV21Test {

    private static final String DB_UPGRADE = "upgrade_v21";
    private static final String DB_FRESH_PROD = "fresh_prod";
    private static final String DB_FRESH_DEV = "fresh_dev";

    @AfterAll
    static void cleanUp() {
        SharedPostgres.dropDatabase(DB_UPGRADE);
        SharedPostgres.dropDatabase(DB_FRESH_PROD);
        SharedPostgres.dropDatabase(DB_FRESH_DEV);
    }

    private static Flyway flyway(String url, String environment, String target) {
        return Flyway.configure()
                .dataSource(url, SharedPostgres.username(), SharedPostgres.password())
                .locations("classpath:db/migration")
                .placeholders(Map.of("environment", environment))
                .target(target)
                .load();
    }

    private static JdbcTemplate jdbc(String url) {
        return new JdbcTemplate(new DriverManagerDataSource(url, SharedPostgres.username(), SharedPostgres.password()));
    }

    private static String sha256Hex(String s) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
    }

    private static int highestOnClasspath() throws Exception {
        int highest = 0;
        for (Resource r : new PathMatchingResourcePatternResolver().getResources("classpath:db/migration/V*__*.sql")) {
            String name = r.getFilename();
            highest = Math.max(highest, Integer.parseInt(name.substring(1, name.indexOf("__"))));
        }
        return highest;
    }

    @Test
    @DisplayName("a database at V21 with legacy data upgrades to the latest: phones normalised, tokens hashed, no data lost")
    void upgradeFromV21KeepsAndConvertsLegacyData() throws Exception {
        String url = SharedPostgres.createDatabase(DB_UPGRADE);
        JdbcTemplate jdbc = jdbc(url);

        long t0 = System.nanoTime();
        flyway(url, "prod", "21").migrate();
        long toV21 = (System.nanoTime() - t0) / 1_000_000;
        assertThat(jdbc.queryForObject("SELECT max(version::int) FROM flyway_schema_history WHERE success", Integer.class)).isEqualTo(21);

        // ---- legacy rows written by the old application (07XXXXXXXX phones, plaintext invite tokens, notification logs)
        jdbc.update("INSERT INTO users (email, phone_number, password_hash, role, verification_status, first_name, last_name) VALUES "
                + "('legacy.donor1@example.org', '0711000011', 'hash', 'DONOR', 'PENDING', 'Leg', 'One'),"
                + "('legacy.donor2@example.org', '0722000022', 'hash', 'DONOR', 'PENDING', 'Leg', 'Two'),"
                + "('legacy.req@example.org',    '0733000033', 'hash', 'REQUESTER', 'VERIFIED', 'Leg', 'Req'),"
                + "('legacy.nine@example.org',   '774000044',  'hash', 'DONOR', 'PENDING', 'Leg', 'Nine'),"
                + "('legacy.cc@example.org',     '94755000055','hash', 'DONOR', 'PENDING', 'Leg', 'Cc'),"
                + "('legacy.e164@example.org',   '+94766000066','hash','DONOR', 'PENDING', 'Leg', 'E164'),"
                // two accounts that normalise to the same number: the second one must be left alone (unique constraint)
                + "('legacy.dup1@example.org',    '+94777000077','hash','DONOR', 'PENDING', 'Leg', 'Dup1'),"
                + "('legacy.dup2@example.org',    '0777000077',  'hash','DONOR', 'PENDING', 'Leg', 'Dup2')");
        jdbc.update("INSERT INTO donor_profiles (user_id, blood_type, date_of_birth, weight_kg, district, latitude, longitude) "
                + "SELECT id, 'O+', DATE '1992-03-04', 68, 'Colombo', 6.93, 79.86 FROM users WHERE email = 'legacy.donor1@example.org'");
        jdbc.update("INSERT INTO blood_requests (requester_id, patient_blood_type, units_needed, urgency, hospital_name, district, latitude, longitude, status, current_radius_km, expires_at) "
                + "SELECT id, 'O+', 2, 'URGENT', 'Legacy Hospital', 'Colombo', 6.93, 79.86, 'OPEN', 5, now() + INTERVAL '1 day' "
                + "FROM users WHERE email = 'legacy.req@example.org'");
        String plainToken = "legacy-plain-invite-token-0123456789abcdef";
        jdbc.update("INSERT INTO request_circles (request_id, invite_token, expires_at) "
                + "SELECT id, ?, now() + INTERVAL '2 days' FROM blood_requests WHERE hospital_name = 'Legacy Hospital'", plainToken);
        jdbc.update("INSERT INTO circle_responses (circle_id, responder_name, responder_phone, blood_type, notes) "
                + "SELECT id, 'Guest Pledger', '0788000088', 'A+', 'will come' FROM request_circles WHERE invite_token = ?", plainToken);
        jdbc.update("INSERT INTO notification_logs (donor_id, request_id, channel, status, recipient, message_body) "
                + "SELECT d.id, r.id, 'SMS', 'SENT', '0711000011', 'Urgent O+ needed at Legacy Hospital' "
                + "FROM donor_profiles d, blood_requests r WHERE r.hospital_name = 'Legacy Hospital'");
        jdbc.update("INSERT INTO notification_logs (donor_id, request_id, channel, status, recipient, message_body) "
                + "SELECT d.id, r.id, 'SMS', 'FAILED', '0711000011', 'second attempt' "
                + "FROM donor_profiles d, blood_requests r WHERE r.hospital_name = 'Legacy Hospital'");
        jdbc.update("INSERT INTO otp_verifications (phone_number, otp_code, is_verified, expires_at) VALUES ('0711000011', '123456', false, now() + INTERVAL '5 minutes')");

        int usersBefore = jdbc.queryForObject("SELECT count(*) FROM users WHERE email LIKE '%@example.org'", Integer.class);
        int profilesBefore = jdbc.queryForObject("SELECT count(*) FROM donor_profiles WHERE district = 'Colombo' AND user_id IN "
                + "(SELECT id FROM users WHERE email LIKE '%@example.org')", Integer.class);

        // ---- upgrade V22 .. latest
        long t1 = System.nanoTime();
        var result = flyway(url, "prod", "latest").migrate();
        long upgradeMs = (System.nanoTime() - t1) / 1_000_000;
        System.out.println("MIGRATION-TIMING upgrade V21->latest: " + result.migrationsExecuted + " migrations in " + upgradeMs
                + " ms (empty->V21: " + toV21 + " ms)");

        int latest = jdbc.queryForObject("SELECT max(version::int) FROM flyway_schema_history WHERE success", Integer.class);
        assertThat(latest).isEqualTo(highestOnClasspath());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE NOT success", Integer.class)).isZero();

        // phones: every convertible number is E.164 now
        Map<String, String> phones = new java.util.HashMap<>();
        jdbc.query("SELECT email, phone_number FROM users WHERE email LIKE 'legacy.%@example.org'",
                rs -> { phones.put(rs.getString(1), rs.getString(2)); });
        assertThat(phones).containsEntry("legacy.donor1@example.org", "+94711000011")
                .containsEntry("legacy.donor2@example.org", "+94722000022")
                .containsEntry("legacy.req@example.org", "+94733000033")
                .containsEntry("legacy.nine@example.org", "+94774000044")
                .containsEntry("legacy.cc@example.org", "+94755000055")
                .containsEntry("legacy.e164@example.org", "+94766000066")
                .containsEntry("legacy.dup1@example.org", "+94777000077")
                // collides with dup1 after normalisation: left as stored, never a unique violation
                .containsEntry("legacy.dup2@example.org", "0777000077");

        // no data loss
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE email LIKE '%@example.org'", Integer.class)).isEqualTo(usersBefore);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM donor_profiles WHERE district = 'Colombo' AND user_id IN "
                + "(SELECT id FROM users WHERE email LIKE '%@example.org')", Integer.class)).isEqualTo(profilesBefore);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM blood_requests WHERE hospital_name = 'Legacy Hospital'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM circle_responses WHERE responder_name = 'Guest Pledger' AND notes = 'will come'", Integer.class)).isEqualTo(1);
        List<Map<String, Object>> logs = jdbc.queryForList("SELECT status, message_body FROM notification_logs ORDER BY id");
        assertThat(logs).hasSize(2);
        assertThat(logs.get(0)).containsEntry("status", "SENT").containsEntry("message_body", "Urgent O+ needed at Legacy Hospital");
        assertThat(logs.get(1)).containsEntry("status", "FAILED").containsEntry("message_body", "second attempt");

        // circle token: SHA-256 hex is the lookup key; the circle itself is untouched and the column may now be emptied
        List<Map<String, Object>> circles = jdbc.queryForList("SELECT invite_token, invite_token_hash FROM request_circles");
        assertThat(circles).hasSize(1);
        assertThat(circles.get(0)).containsEntry("invite_token_hash", sha256Hex(plainToken));
        assertThat(jdbc.queryForObject("SELECT is_nullable FROM information_schema.columns "
                + "WHERE table_name = 'request_circles' AND column_name = 'invite_token'", String.class)).isEqualTo("YES");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_indexes WHERE indexname = 'uq_request_circles_token_hash'", Integer.class)).isEqualTo(1);

        // documented, deliberate: pending plaintext OTP codes are dropped by V25 (they live <= 40 minutes)
        assertThat(jdbc.queryForObject("SELECT count(*) FROM otp_verifications", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT character_maximum_length FROM information_schema.columns "
                + "WHERE table_name = 'otp_verifications' AND column_name = 'otp_code'", Integer.class)).isEqualTo(64);

        // demo seed (V3/V10) is gone in prod mode
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE email LIKE '%@test.com' AND password_hash <> '!disabled-demo-account'", Integer.class)).isZero();

        // a second migrate is a no-op (idempotent), and Flyway validates the applied checksums
        assertThat(flyway(url, "prod", "latest").migrate().migrationsExecuted).isZero();
        flyway(url, "prod", "latest").validate();
    }

    @Test
    @DisplayName("empty database -> latest in prod placeholder mode: every migration applies, demo seed removed")
    void freshDatabaseProdMode() throws Exception {
        String url = SharedPostgres.createDatabase(DB_FRESH_PROD);
        long t = System.nanoTime();
        var result = flyway(url, "prod", "latest").migrate();
        long ms = (System.nanoTime() - t) / 1_000_000;
        System.out.println("MIGRATION-TIMING empty->latest (environment=prod): " + result.migrationsExecuted + " migrations in " + ms + " ms");

        JdbcTemplate jdbc = jdbc(url);
        assertThat(result.migrationsExecuted).isEqualTo(new PathMatchingResourcePatternResolver().getResources("classpath:db/migration/V*__*.sql").length);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE NOT success", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM blood_bank_inventory", Integer.class)).isZero();
        assertThat(jdbc.queryForList("SELECT extname FROM pg_extension", String.class)).contains("cube", "earthdistance");
        flyway(url, "prod", "latest").validate();
    }

    @Test
    @DisplayName("empty database -> latest in dev placeholder mode: every migration applies, demo seed kept")
    void freshDatabaseDevMode() throws Exception {
        String url = SharedPostgres.createDatabase(DB_FRESH_DEV);
        long t = System.nanoTime();
        var result = flyway(url, "dev", "latest").migrate();
        long ms = (System.nanoTime() - t) / 1_000_000;
        System.out.println("MIGRATION-TIMING empty->latest (environment=dev): " + result.migrationsExecuted + " migrations in " + ms + " ms");

        JdbcTemplate jdbc = jdbc(url);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE NOT success", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE email LIKE '%@test.com'", Integer.class)).isPositive();
        for (MigrationInfo info : flyway(url, "dev", "latest").info().all()) {
            assertThat(info.getState().isApplied()).as("migration %s", info.getVersion()).isTrue();
        }
    }
}
