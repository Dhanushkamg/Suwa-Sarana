package com.suwasarana.api.integration;

import com.suwasarana.api.support.PostgresIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Boots the whole application on a real PostgreSQL 16, applies EVERY Flyway migration and lets Hibernate validate all
 * entities against the resulting schema ({@code ddl-auto=validate}: a mapping/migration mismatch fails the context).
 */
class PostgresSmokeTest extends PostgresIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("every migration on the classpath is applied successfully, in order")
    void allMigrationsApplied() throws Exception {
        int onClasspath = 0;
        int highest = 0;
        for (Resource r : new PathMatchingResourcePatternResolver().getResources("classpath:db/migration/V*__*.sql")) {
            onClasspath++;
            String name = r.getFilename();
            highest = Math.max(highest, Integer.parseInt(name.substring(1, name.indexOf("__"))));
        }
        Integer applied = jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success AND version IS NOT NULL", Integer.class);
        Integer failed = jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE NOT success", Integer.class);
        String latest = jdbc.queryForObject(
                "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1", String.class);

        assertThat(onClasspath).isGreaterThanOrEqualTo(23);
        assertThat(applied).isEqualTo(onClasspath);
        assertThat(failed).isZero();
        assertThat(Integer.parseInt(latest)).isEqualTo(highest);
    }

    @Test
    @DisplayName("PostgreSQL contrib extensions needed by geo matching are installed")
    void geoExtensionsInstalled() {
        List<String> ext = jdbc.queryForList("SELECT extname FROM pg_extension", String.class);
        assertThat(ext).contains("cube", "earthdistance");
    }

    @Test
    @DisplayName("V22 removed the seeded demo accounts and data (environment=prod)")
    void demoDataRemoved() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE email LIKE '%@test.com'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM blood_requests WHERE hospital_name IN "
                + "('National Hospital of Sri Lanka', 'Teaching Hospital Jaffna')", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM request_circles WHERE invite_token = 'circle-demo-colombo-2026'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM blood_bank_inventory", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM donation_camps WHERE name LIKE 'Colombo Red Cross%'", Integer.class)).isZero();
    }

    @Test
    @DisplayName("V23 indexes exist")
    void indexesCreated() {
        List<String> indexes = jdbc.queryForList("SELECT indexname FROM pg_indexes WHERE schemaname = 'public'", String.class);
        assertThat(indexes).contains(
                "idx_requests_requester", "idx_matches_donor", "idx_camps_organizer", "idx_camps_status_date",
                "idx_users_verification_status", "idx_refresh_tokens_expires_at", "idx_donor_profiles_user_id");
    }

    @Test
    @DisplayName("readiness (with database) and liveness probes are UP and public")
    void healthProbesAreUp() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("every response carries a request id and API security headers")
    void responsesCarryPlatformHeaders() throws Exception {
        mockMvc.perform(get("/api/camps"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }
}
