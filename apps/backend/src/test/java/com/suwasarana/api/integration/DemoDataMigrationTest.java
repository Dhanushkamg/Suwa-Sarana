package com.suwasarana.api.integration;

import com.suwasarana.api.support.DockerAvailableCondition;
import com.suwasarana.api.support.SharedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives Flyway directly on throw-away databases to prove V22 (demo-data removal):
 * kept in the dev environment, removed in prod, and never touching real data - including real donors that booked a
 * slot at a demo hospital.
 */
@ExtendWith(DockerAvailableCondition.class)
class DemoDataMigrationTest {

    private static final String SQL_USERS_DEMO = "SELECT count(*) FROM users WHERE email LIKE '%@test.com'";

    private JdbcTemplate migrate(String dbName, String environment, String targetVersion) throws Exception {
        String url = SharedPostgres.createDatabase(dbName);
        Flyway.configure()
                .dataSource(url, SharedPostgres.username(), SharedPostgres.password())
                .locations("classpath:db/migration")
                .placeholders(Map.of("environment", environment))
                .target(targetVersion)
                .load()
                .migrate();
        return new JdbcTemplate(new DriverManagerDataSource(url, SharedPostgres.username(), SharedPostgres.password()));
    }

    private void migrateTo(String dbName, String environment, String targetVersion) throws Exception {
        String url = SharedPostgres.jdbcUrl();
        String dbUrl = url.substring(0, url.lastIndexOf('/') + 1) + dbName + (url.contains("?") ? url.substring(url.indexOf('?')) : "");
        Flyway.configure()
                .dataSource(dbUrl, SharedPostgres.username(), SharedPostgres.password())
                .locations("classpath:db/migration")
                .placeholders(Map.of("environment", environment))
                .target(targetVersion)
                .load()
                .migrate();
    }

    @Test
    @DisplayName("environment=dev keeps the demo accounts and data")
    void devKeepsDemoData() throws Exception {
        JdbcTemplate jdbc = migrate("v22_dev_pgtest", "dev", "latest");
        assertThat(jdbc.queryForObject(SQL_USERS_DEMO, Integer.class)).isEqualTo(7);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM blood_requests", Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM blood_bank_inventory", Integer.class)).isEqualTo(8);
    }

    @Test
    @DisplayName("environment=prod removes the demo data but keeps real data and demo users that real bookings depend on")
    void prodRemovesOnlyDemoData() throws Exception {
        // schema + demo seed as production databases have it today (up to V21)
        JdbcTemplate jdbc = migrate("v22_prod_pgtest", "prod", "21");
        assertThat(jdbc.queryForObject(SQL_USERS_DEMO, Integer.class)).isEqualTo(7);

        // --- real data created after launch
        jdbc.update("INSERT INTO users (email, phone_number, password_hash, role, verification_status, first_name, last_name) VALUES "
                + "('real.donor@example.org', '0711000001', 'hash', 'DONOR', 'PENDING', 'Real', 'Donor'),"
                + "('real.requester@example.org', '0711000002', 'hash', 'REQUESTER', 'VERIFIED', 'Real', 'Requester'),"
                + "('real.clinic@example.org', '0711000003', 'hash', 'HOSPITAL_REQUESTER', 'VERIFIED', 'Real', 'Clinic')");
        jdbc.update("INSERT INTO donor_profiles (user_id, blood_type, date_of_birth, weight_kg, district, latitude, longitude) "
                + "SELECT id, 'O+', DATE '1995-01-01', 70, 'Colombo', 6.93, 79.86 FROM users WHERE email = 'real.donor@example.org'");
        jdbc.update("INSERT INTO blood_requests (requester_id, patient_blood_type, units_needed, urgency, hospital_name, district, latitude, longitude, status, current_radius_km, expires_at) "
                + "SELECT id, 'O+', 1, 'URGENT', 'Real Hospital', 'Colombo', 6.93, 79.86, 'OPEN', 5, now() + INTERVAL '1 day' "
                + "FROM users WHERE email = 'real.requester@example.org'");
        // a real donor matched to a REAL request is real data
        jdbc.update("INSERT INTO request_matches (request_id, donor_id, distance_km, response) "
                + "SELECT r.id, d.id, 1.0, 'PENDING' FROM blood_requests r, donor_profiles d "
                + "WHERE r.hospital_name = 'Real Hospital' AND d.district = 'Colombo' AND d.user_id = (SELECT id FROM users WHERE email='real.donor@example.org')");
        // a slot at the real clinic, booked by the real donor (kept), and a slot at the demo hospital booked by the real donor
        jdbc.update("INSERT INTO donation_slots (hospital_id, start_time, end_time, capacity, booked_count) "
                + "SELECT id, now() + INTERVAL '2 days', now() + INTERVAL '2 days 1 hour', 5, 1 FROM users WHERE email = 'real.clinic@example.org'");
        jdbc.update("INSERT INTO donation_slots (hospital_id, start_time, end_time, capacity, booked_count) "
                + "SELECT id, now() + INTERVAL '3 days', now() + INTERVAL '3 days 1 hour', 5, 1 FROM users WHERE email = 'hospital@test.com'");
        jdbc.update("INSERT INTO slot_bookings (slot_id, donor_id) SELECT s.id, d.id FROM donation_slots s, donor_profiles d "
                + "WHERE d.user_id = (SELECT id FROM users WHERE email='real.donor@example.org')");
        // a demo donor booked a slot at the real clinic: that booking is demo data and must go
        jdbc.update("INSERT INTO slot_bookings (slot_id, donor_id) SELECT s.id, d.id FROM donation_slots s, donor_profiles d "
                + "WHERE s.hospital_id = (SELECT id FROM users WHERE email='real.clinic@example.org') "
                + "AND d.user_id = (SELECT id FROM users WHERE email='donor@test.com')");
        int realBookingsBefore = jdbc.queryForObject("SELECT count(*) FROM slot_bookings", Integer.class);

        // --- V22
        migrateTo("v22_prod_pgtest", "prod", "22");

        // real data untouched
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE email LIKE '%@example.org'", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM blood_requests WHERE hospital_name = 'Real Hospital'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM request_matches", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM donation_slots WHERE capacity = 5", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM slot_bookings", Integer.class)).isEqualTo(realBookingsBefore - 1);

        // demo data gone ...
        assertThat(jdbc.queryForObject("SELECT count(*) FROM blood_requests WHERE hospital_name <> 'Real Hospital'", Integer.class)).isZero();
        assertThat(jdbc.queryForList("SELECT name || ' / ' || organizer_id FROM donation_camps", String.class)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM request_circles", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM blood_bank_inventory", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE email IN ('admin@test.com','donor@test.com','donor2@test.com',"
                + "'donor3@test.com','requester@test.com','pending_hospital@test.com')", Integer.class)).isZero();

        // ... except the demo hospital that a real donor booked: kept, and nobody can log in as it
        assertThat(jdbc.queryForObject("SELECT password_hash FROM users WHERE email = 'hospital@test.com'", String.class))
                .isEqualTo("!disabled-demo-account");
    }

    @Test
    @DisplayName("V22 is idempotent: running it on a database without demo data deletes nothing")
    void noDemoDataNoDeletes() throws Exception {
        JdbcTemplate jdbc = migrate("v22_clean_pgtest", "prod", "latest");
        jdbc.update("INSERT INTO users (email, phone_number, password_hash, role, verification_status, first_name, last_name) "
                + "VALUES ('keep.me@example.org', '0711999999', 'hash', 'DONOR', 'PENDING', 'Keep', 'Me')");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users", Integer.class)).isEqualTo(1);
    }
}
