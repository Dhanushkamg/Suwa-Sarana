package com.suwasarana.api.matching;

import com.suwasarana.api.analytics.AnalyticsService;
import com.suwasarana.api.analytics.dto.DistrictSummaryDto;
import com.suwasarana.api.analytics.dto.NationalSummaryDto;
import com.suwasarana.api.demo.DemoDataSeeder;
import com.suwasarana.api.inventory.InventoryService;
import com.suwasarana.api.inventory.dto.InventoryDtos.StockSignalDto;
import com.suwasarana.api.request.BloodRequest;
import com.suwasarana.api.request.RequestRepository;
import com.suwasarana.api.request.RequestStatus;
import com.suwasarana.api.request.Urgency;
import com.suwasarana.api.shared.AppClock;
import com.suwasarana.api.user.Role;
import com.suwasarana.api.user.User;
import com.suwasarana.api.user.UserRepository;
import com.suwasarana.api.user.VerificationStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.jpa.repository.Query;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the matching SQL, the Flyway migrations, the entities and the demo seeder against a REAL
 * PostgreSQL: the dev container {@code suwa-pg-dev} on localhost:5433 (docker start suwa-pg-dev).
 * A scratch database is created for the class and dropped afterwards. When the server cannot be reached the whole class
 * is skipped, so CI without Docker stays green (H2 cannot run earthdistance).
 */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MatchingPostgresTest {

    private static final String HOST = System.getProperty("suwa.pg.host", "localhost");
    private static final String PORT = System.getProperty("suwa.pg.port", "5433");
    private static final String USER = System.getProperty("suwa.pg.user", "postgres");
    private static final String PASSWORD = System.getProperty("suwa.pg.password", "devonly123");
    private static final String ADMIN_URL = "jdbc:postgresql://" + HOST + ":" + PORT + "/postgres";

    private static String dbName;

    @BeforeAll
    static void createScratchDatabase() {
        String name = "suwa_c_test_" + System.currentTimeMillis();
        boolean ok;
        try (Connection c = DriverManager.getConnection(ADMIN_URL, USER, PASSWORD); Statement st = c.createStatement()) {
            st.execute("CREATE DATABASE " + name);
            ok = true;
        } catch (Exception e) {
            ok = false;
            System.out.println("PostgreSQL not reachable at " + ADMIN_URL + ": " + e.getMessage());
        }
        Assumptions.assumeTrue(ok, "PostgreSQL dev container not reachable on " + HOST + ":" + PORT + " (docker start suwa-pg-dev); skipping");
        dbName = name;

        // Run every migration with Flyway directly (the same V1..Vn files the application ships), then drop the demo rows
        // that old migrations (V3, V10) insert: nothing here may depend on seeded data.
        String url = "jdbc:postgresql://" + HOST + ":" + PORT + "/" + name;
        org.flywaydb.core.Flyway.configure().dataSource(url, USER, PASSWORD).locations("classpath:db/migration").load().migrate();
        try (Connection c = DriverManager.getConnection(url, USER, PASSWORD); Statement st = c.createStatement()) {
            st.execute("TRUNCATE users, blood_bank_inventory RESTART IDENTITY CASCADE");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @AfterAll
    static void dropScratchDatabase() {
        if (dbName == null) return;
        try (Connection c = DriverManager.getConnection(ADMIN_URL, USER, PASSWORD); Statement st = c.createStatement()) {
            st.execute("DROP DATABASE IF EXISTS " + dbName + " WITH (FORCE)");
        } catch (Exception e) {
            System.out.println("Could not drop scratch database " + dbName + ": " + e.getMessage());
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> "jdbc:postgresql://" + HOST + ":" + PORT + "/" + dbName);
        r.add("spring.datasource.username", () -> USER);
        r.add("spring.datasource.password", () -> PASSWORD);
        // The entities must match what the migrations created
        r.add("spring.flyway.enabled", () -> "false"); // migrated in @BeforeAll
        r.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        r.add("app.demo-data.enabled", () -> "true");
        r.add("DEMO_PASSWORD", () -> "PgTestPass#1");
    }

    @Autowired private JdbcTemplate jdbc;
    @Autowired private MatchingEngineRepository candidates;
    @Autowired private MatchingEngine matchingEngine;
    @Autowired private RequestRepository requestRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private AnalyticsService analytics;
    @Autowired private InventoryService inventory;
    @Autowired private DemoDataSeeder seeder;
    @Autowired private AppClock clock;

    private static final double LAT = 6.9271;
    private static final double LON = 79.8612;

    // ------------------------------------------------------------------ migrations and demo data

    @Test
    @Order(1)
    @DisplayName("All migrations apply on PostgreSQL and the new tables exist")
    void migrationsApply() {
        Integer applied = jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success", Integer.class);
        Integer latest = jdbc.queryForObject("SELECT MAX(CAST(version AS INTEGER)) FROM flyway_schema_history WHERE success AND version IS NOT NULL", Integer.class);
        assertNotNull(applied);
        assertTrue(latest >= 33, "latest migration " + latest);
        for (String table : List.of("user_notifications", "donation_records", "inventory_units", "inventory_thresholds",
                "inventory_movements", "inventory_sites")) {
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_name = ?", Integer.class, table), table);
        }
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_name='blood_requests' AND column_name='closed_at'", Integer.class));
    }

    @Test
    @Order(2)
    @DisplayName("The demo seeder fills a real database: every role, all 25 districts, non-zero analytics")
    void demoData_seededOnPostgres_andAnalyticsAreNonZero() {
        // it already ran at start-up; a second run must do nothing
        assertTrue(seeder.seed().isEmpty(), "seeding is idempotent");

        Map<String, Integer> byRole = new java.util.HashMap<>();
        jdbc.query("SELECT role, COUNT(*) c FROM users GROUP BY role", rs -> {
            byRole.put(rs.getString(1), rs.getInt(2));
        });
        for (Role role : Role.values()) assertTrue(byRole.getOrDefault(role.name(), 0) > 0, "no users with role " + role);
        assertEquals(60, byRole.get("DONOR"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE verification_status = 'PENDING' AND role = 'HOSPITAL_REQUESTER'", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE verification_status = 'REJECTED' AND role = 'BLOOD_BANK_REQUESTER'", Integer.class));
        assertEquals(25, jdbc.queryForObject("SELECT COUNT(DISTINCT district) FROM donor_profiles", Integer.class));
        assertEquals(25, jdbc.queryForObject("SELECT COUNT(DISTINCT district) FROM blood_requests", Integer.class));
        for (RequestStatus s : RequestStatus.values()) {
            assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM blood_requests WHERE status = ?", Integer.class, s.name()) > 0, "no request in " + s);
        }

        List<DistrictSummaryDto> districts = analytics.districtSummaries(true);
        assertEquals(25, districts.size());
        assertTrue(districts.stream().allMatch(d -> d.getRegisteredDonors() > 0), "every district has donors");
        assertTrue(districts.stream().mapToLong(DistrictSummaryDto::getActiveRequests).sum() > 0);
        assertTrue(districts.stream().anyMatch(d -> d.getFulfillmentRate() != null));

        NationalSummaryDto n = analytics.nationalSummary(true);
        assertTrue(n.getActiveRequests() > 0);
        assertTrue(n.getAvailableDonors() > 0);
        assertNotNull(n.getFulfillmentRate());
        assertEquals(60, n.getRegisteredDonors());
        assertEquals(25, n.getDistrictsWithData());
        assertEquals(jdbc.queryForObject("SELECT COUNT(*) FROM blood_requests WHERE status IN ('OPEN','ESCALATING','MATCHED') AND expires_at > ?",
                Long.class, java.sql.Timestamp.valueOf(clock.now())), n.getActiveRequests());
        // pooled national rate = fulfilled / closed (never an average of district rates)
        assertEquals(AnalyticsServiceAccess.rate(n.getFulfilledRequests(), n.getClosedRequests()), n.getFulfillmentRate());

        // the expiry job clears the deliberately expired demo batch; the public ticker shows the demo shortages
        assertTrue(inventory.expireBatches() >= 1);
        List<StockSignalDto> signals = inventory.publicSignals();
        assertFalse(signals.isEmpty(), "demo shortages are visible");
        assertTrue(signals.stream().anyMatch(s -> s.bloodType.equals("O-") && s.status.equals("CRITICAL") && s.district.equals("Colombo")));
        assertTrue(signals.stream().noneMatch(s -> s.hospitalName.contains("Trincomalee")), "the stale institution is hidden");
    }

    // ------------------------------------------------------------------ the index

    @Test
    @Order(3)
    @DisplayName("With 20,000 donors the candidate query is answered from the GiST index (earth_box), not a sequential scan")
    void candidateQuery_usesTheGistIndex() throws Exception {
        clearDonorData();
        User u = newUser("bulk");
        jdbc.update("INSERT INTO donor_profiles (user_id, blood_type, date_of_birth, weight_kg, district, latitude, longitude, is_available, reliability_score) "
                + "SELECT ?, (ARRAY['O+','A+','B+','AB+','O-','A-','B-','AB-'])[1 + floor(random() * 8)::int], DATE '1990-05-05', 65, 'Colombo', "
                + "5.9 + random() * 3.9, 79.7 + random() * 2.1, true, round((random() * 100)::numeric, 2) FROM generate_series(1, 20000)", u.getId());
        jdbc.execute("ANALYZE donor_profiles");
        assertEquals(20000, jdbc.queryForObject("SELECT COUNT(*) FROM donor_profiles", Integer.class));

        String sql = candidateSqlWithLiterals(5000);
        String plan = String.join("\n", jdbc.queryForList("EXPLAIN (ANALYZE, BUFFERS OFF) " + sql, String.class));
        System.out.println("===== candidate query plan (20,000 donors, 5 km) =====\n" + plan);
        assertTrue(plan.contains("idx_donor_geo"), "the GiST index must be used:\n" + plan);
        assertFalse(plan.contains("Seq Scan on donor_profiles"), "no sequential scan of donor_profiles:\n" + plan);

        // For the record: the old predicate (earth_distance only) cannot use the spatial index; it filters every donor of the blood type by trigonometry
        String oldPlan = String.join("\n", jdbc.queryForList(
                "EXPLAIN SELECT d.id FROM donor_profiles d WHERE d.blood_type = 'A+' AND d.is_available = true "
                        + "AND (earth_distance(ll_to_earth(6.9271, 79.8612), ll_to_earth(d.latitude, d.longitude)) / 1000) <= 5", String.class));
        System.out.println("===== old predicate plan =====\n" + oldPlan);
        assertFalse(oldPlan.contains("idx_donor_geo"), "the old predicate cannot use the spatial index:\n" + oldPlan);

        // And the repository method returns only donors inside the circle, compatible and capped
        List<Object[]> rows = candidates.findCandidateRows(BloodCompatibility.compatibleDonorTypes("A+"), "A+", LAT, LON, 5000.0,
                1L, clock.today(), false, 100);
        assertFalse(rows.isEmpty());
        assertTrue(rows.size() <= 100);
        for (Object[] row : rows) assertTrue(((Number) row[1]).doubleValue() <= 5.0 + 1e-6);
    }

    // ------------------------------------------------------------------ ordering and filters

    private long insertDonor(User u, String type, double km, int reliability, boolean available) {
        double lat = LAT + km / 111.19;
        return jdbc.queryForObject("INSERT INTO donor_profiles (user_id, blood_type, date_of_birth, weight_kg, district, latitude, longitude, is_available, reliability_score) "
                        + "VALUES (?, ?, DATE '1990-05-05', 70, 'Colombo', ?, ?, ?, ?) RETURNING id",
                Long.class, u.getId(), type, lat, LON, available, reliability);
    }

    private List<Long> ids(List<Object[]> rows) {
        return rows.stream().map(r -> ((Number) r[0]).longValue()).toList();
    }

    @Test
    @Order(4)
    @DisplayName("Compatible types only; unavailable, deferred, already-matched and out-of-range donors are excluded")
    void filters() {
        clearDonorData();
        User u = newUser("filters");
        long near = insertDonor(u, "A+", 1, 50, true);
        long universal = insertDonor(u, "O-", 2, 50, true);
        long incompatible = insertDonor(u, "B+", 1, 50, true);
        long unavailable = insertDonor(u, "A+", 0.5, 50, false);
        long deferred = insertDonor(u, "A+", 0.8, 50, true);
        long alreadyMatched = insertDonor(u, "A+", 0.9, 50, true);
        long outside = insertDonor(u, "A+", 8, 50, true);
        jdbc.update("INSERT INTO donor_deferrals (donor_id, reason, deferred_from, deferred_until) VALUES (?, 'RECENT_DONATION', CURRENT_DATE - 10, CURRENT_DATE + 10)", deferred);
        User owner = newUser("owner");
        BloodRequest r = saveRequest(owner, Urgency.URGENT, "A+");
        jdbc.update("INSERT INTO request_matches (request_id, donor_id, distance_km, response) VALUES (?, ?, 1, 'PENDING')", r.getId(), alreadyMatched);

        List<Long> found = ids(candidates.findCandidateRows(BloodCompatibility.compatibleDonorTypes("A+"), "A+", LAT, LON, 5000.0,
                r.getId(), clock.today(), false, 100));

        assertEquals(java.util.Set.of(near, universal), java.util.Set.copyOf(found));
        assertFalse(found.contains(incompatible));
        assertFalse(found.contains(unavailable));
        assertFalse(found.contains(deferred));
        assertFalse(found.contains(alreadyMatched));
        assertFalse(found.contains(outside));
    }

    @Test
    @Order(5)
    @DisplayName("CRITICAL: nearest first. Otherwise: reliability-weighted score with a bonus for the exact blood type")
    void ordering() {
        clearDonorData();
        User u = newUser("ordering");
        long near = insertDonor(u, "A+", 1, 40, true);        // score 0.6*40 + 0.4*80 + 10 = 66
        long farReliable = insertDonor(u, "A+", 4, 95, true); // score 0.6*95 + 0.4*20 + 10 = 75
        long universal = insertDonor(u, "O-", 2, 90, true);   // score 0.6*90 + 0.4*60 + 0 = 78
        List<String> types = BloodCompatibility.compatibleDonorTypes("A+");

        List<Object[]> critical = candidates.findCandidateRows(types, "A+", LAT, LON, 5000.0, 99L, clock.today(), true, 100);
        assertEquals(List.of(near, universal, farReliable), ids(critical), "CRITICAL is distance-first");

        List<Object[]> routine = candidates.findCandidateRows(types, "A+", LAT, LON, 5000.0, 99L, clock.today(), false, 100);
        assertEquals(List.of(universal, farReliable, near), ids(routine), "routine is reliability-weighted");

        // the stored distance is real: about 1, 2 and 4 km
        assertEquals(1.0, ((Number) critical.get(0)[1]).doubleValue(), 0.05);
        assertEquals(2.0, ((Number) critical.get(1)[1]).doubleValue(), 0.05);
        assertEquals(4.0, ((Number) critical.get(2)[1]).doubleValue(), 0.05);
    }

    @Test
    @Order(6)
    @DisplayName("Engine end to end: matches get a distance, are alerted after commit, and eligibility rules apply")
    void engineEndToEnd() {
        clearDonorData();
        User u = newUser("engine");
        long eligible = insertDonor(u, "A+", 1, 60, true);
        long underweight = insertDonor(u, "A+", 2, 60, true);
        jdbc.update("UPDATE donor_profiles SET weight_kg = 48 WHERE id = ?", underweight);
        User owner = newUser("engineowner");
        BloodRequest r = saveRequest(owner, Urgency.CRITICAL, "A+");

        matchingEngine.runMatchingForRequest(r.getId());

        List<Map<String, Object>> matches = jdbc.queryForList("SELECT donor_id, distance_km, response FROM request_matches WHERE request_id = ?", r.getId());
        assertEquals(1, matches.size(), "the underweight donor is not matched");
        assertEquals(eligible, ((Number) matches.get(0).get("donor_id")).longValue());
        assertEquals(1.0, ((BigDecimal) matches.get(0).get("distance_km")).doubleValue(), 0.05);
        assertEquals("PENDING", matches.get(0).get("response"));
        // alerts were written after the transaction committed: inbox row + delivery log (nobody on SSE, so SMS)
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM user_notifications WHERE user_id = ? AND type = 'NEW_MATCH'", Integer.class, u.getId()));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM notification_logs WHERE request_id = ? AND channel = 'SMS'", Integer.class, r.getId()));

        // running again does not alert the same donor twice
        matchingEngine.runMatchingForRequest(r.getId());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM request_matches WHERE request_id = ?", Integer.class, r.getId()));
    }

    // ------------------------------------------------------------------ helpers

    private void clearDonorData() {
        jdbc.execute("TRUNCATE donor_profiles, request_matches, notification_logs, user_notifications, donation_records, slot_bookings, "
                + "camp_registrations, donor_deferrals RESTART IDENTITY CASCADE");
    }

    private User newUser(String name) {
        User u = new User();
        u.setEmail(name + System.nanoTime() + "@pg.test");
        u.setPhoneNumber("07" + (System.nanoTime() % 100000000L));
        u.setFirstName(name);
        u.setLastName("Pg");
        u.setRole(Role.DONOR);
        u.setVerificationStatus(VerificationStatus.VERIFIED);
        return userRepository.save(u);
    }

    private BloodRequest saveRequest(User owner, Urgency urgency, String type) {
        BloodRequest r = new BloodRequest();
        r.setRequester(owner);
        r.setPatientBloodType(type);
        r.setUnitsNeeded((short) 1);
        r.setUrgency(urgency);
        r.setHospitalName("National Hospital");
        r.setDistrict("Colombo");
        r.setLatitude(LAT);
        r.setLongitude(LON);
        r.setStatus(RequestStatus.ESCALATING);
        r.setCurrentRadiusKm((short) 5);
        r.setCreatedAt(clock.now());
        r.setLastEscalatedAt(clock.now());
        r.setExpiresAt(clock.now().plusHours(6));
        return requestRepository.save(r);
    }

    /** The exact SQL of the repository method with every :parameter replaced by a literal, so EXPLAIN shows the real plan. */
    private String candidateSqlWithLiterals(int radiusMetres) throws Exception {
        Query q = MatchingEngineRepository.class.getMethod("findCandidateRows", java.util.Collection.class, String.class, double.class,
                double.class, double.class, Long.class, LocalDate.class, boolean.class, int.class).getAnnotation(Query.class);
        Map<String, String> literals = Map.of(
                "types", "'A+','A-','O+','O-'", "exactType", "'A+'", "lat", String.valueOf(LAT), "lon", String.valueOf(LON),
                "radiusM", String.valueOf(radiusMetres), "requestId", "1", "today", "DATE '" + clock.today() + "'",
                "critical", "false", "limit", "100");
        Matcher m = Pattern.compile(":([a-zA-Z]+)").matcher(q.value());
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String lit = literals.get(m.group(1));
            if (lit == null) throw new IllegalStateException("unmapped parameter " + m.group(1));
            m.appendReplacement(out, Matcher.quoteReplacement(lit));
        }
        m.appendTail(out);
        return out.toString();
    }

    /** Same rounding rule as the service, so the test does not re-derive it differently. */
    private static final class AnalyticsServiceAccess {
        static Double rate(long fulfilled, long closed) {
            if (closed <= 0) return null;
            return BigDecimal.valueOf(fulfilled * 100.0 / closed).setScale(1, java.math.RoundingMode.HALF_UP).doubleValue();
        }
    }
}
