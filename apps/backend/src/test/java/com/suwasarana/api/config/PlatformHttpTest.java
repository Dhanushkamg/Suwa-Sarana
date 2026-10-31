package com.suwasarana.api.config;

import com.suwasarana.api.security.JwtTokenProvider;
import com.suwasarana.api.user.Role;
import com.suwasarana.api.user.User;
import com.suwasarana.api.user.UserRepository;
import com.suwasarana.api.user.VerificationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Clock;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Platform behaviour on the H2 test profile: request ids, security headers, JSON error envelope, actuator access,
 * the springdoc/Spring Boot 4 compatibility check (/v3/api-docs as ADMIN) and the business-time Clock.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class PlatformHttpTest {

    private static final AtomicInteger IP = new AtomicInteger(1);

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository users;
    @Autowired private JwtTokenProvider jwt;
    @Autowired private Clock clock;

    private String adminToken;
    private String donorToken;

    private static RequestPostProcessor freshIp() {
        int n = IP.getAndIncrement();
        return r -> {
            r.setRemoteAddr("10.88.0." + (n % 250 + 1));
            return r;
        };
    }

    private String tokenFor(Role role) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        User u = new User();
        u.setEmail(role.name().toLowerCase() + "." + id + "@example.org");
        u.setPhoneNumber("07" + Math.abs(id.hashCode() % 90_000_000) + 10_000_000);
        u.setFirstName("Plat");
        u.setLastName("Form");
        u.setRole(role);
        u.setVerificationStatus(VerificationStatus.VERIFIED);
        u.setPasswordHash("{noop}unused");
        users.save(u);
        return jwt.generateTokenFromUsername(u.getEmail());
    }

    @BeforeEach
    void tokens() {
        adminToken = tokenFor(Role.ADMIN);
        donorToken = tokenFor(Role.DONOR);
    }

    // ---------------------------------------------------------------- request id

    @Test
    @DisplayName("a request id is generated, returned and reused when the caller sends a safe one")
    void requestId() throws Exception {
        String generated = mockMvc.perform(get("/api/camps")).andExpect(status().isOk())
                .andExpect(header().exists("X-Request-Id")).andReturn().getResponse().getHeader("X-Request-Id");
        assertThat(generated).matches("[0-9a-f-]{36}");

        mockMvc.perform(get("/api/camps").header("X-Request-Id", "alb-1234567890abcdef"))
                .andExpect(header().string("X-Request-Id", "alb-1234567890abcdef"));
    }

    @Test
    @DisplayName("an unsafe caller-supplied request id is replaced (no log injection)")
    void unsafeRequestIdReplaced() throws Exception {
        String id = mockMvc.perform(get("/api/camps").header("X-Request-Id", "bad id\nforged log line"))
                .andReturn().getResponse().getHeader("X-Request-Id");
        assertThat(id).matches("[0-9a-f-]{36}");
    }

    @Test
    @DisplayName("even rejected requests carry the request id, in the header and in the JSON error body")
    void errorBodyHasRequestId() throws Exception {
        mockMvc.perform(get("/api/requests/me").header("X-Request-Id", "trace-abcdef123456"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Request-Id", "trace-abcdef123456"))
                .andExpect(jsonPath("$.requestId").value("trace-abcdef123456"))
                .andExpect(jsonPath("$.code").value("auth.unauthorized"))
                .andExpect(jsonPath("$.success").value(false));
    }

    // ---------------------------------------------------------------- security headers

    @Test
    @DisplayName("API responses carry the explicit security headers")
    void securityHeaders() throws Exception {
        mockMvc.perform(get("/api/camps"))
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Permissions-Policy", containsString("geolocation=()")))
                .andExpect(header().string("Cache-Control", containsString("no-store")));
    }

    @Test
    @DisplayName("HSTS is sent on HTTPS requests only")
    void hsts() throws Exception {
        mockMvc.perform(get("/api/camps").secure(true))
                .andExpect(header().string("Strict-Transport-Security", containsString("max-age=31536000")))
                .andExpect(header().string("Strict-Transport-Security", containsString("includeSubDomains")));
        mockMvc.perform(get("/api/camps")).andExpect(header().doesNotExist("Strict-Transport-Security"));
    }

    // ---------------------------------------------------------------- error envelope

    @Test
    @DisplayName("403 for a wrong role uses the envelope with code auth.forbidden")
    void forbiddenEnvelope() throws Exception {
        mockMvc.perform(get("/api/admin/reports").header("Authorization", "Bearer " + donorToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("auth.forbidden"))
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("validation failures: code validation.failed, data={field: field-level code}, message = English fallback")
    void validationEnvelope() throws Exception {
        mockMvc.perform(post("/api/auth/login").with(freshIp()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.data.password").value("auth.password.required"));
    }

    @Test
    @DisplayName("framework errors (unknown route, bad method, malformed JSON) get the envelope and a code")
    void frameworkErrors() throws Exception {
        mockMvc.perform(get("/api/does-not-exist").header("Authorization", "Bearer " + donorToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("http.not_found"));
        mockMvc.perform(post("/api/auth/login").with(freshIp()).contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("request.malformed"));
    }

    @Test
    @DisplayName("error bodies never contain stack traces or exception class names")
    void noInternals() throws Exception {
        mockMvc.perform(post("/api/auth/login").with(freshIp()).contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(content().string(not(containsString("Exception"))))
                .andExpect(content().string(not(containsString("at com."))));
    }

    @Test
    @DisplayName("dates in API responses are ISO strings")
    void timestampIsString() throws Exception {
        mockMvc.perform(get("/api/requests/me"))
                .andExpect(jsonPath("$.timestamp").isString());
    }

    // ---------------------------------------------------------------- actuator

    @Test
    @DisplayName("health probes are public, everything else on the actuator is ADMIN only")
    void actuatorAccess() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        mockMvc.perform(get("/actuator/health")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/metrics").header("Authorization", "Bearer " + donorToken)).andExpect(status().isForbidden());
        mockMvc.perform(get("/actuator/metrics").header("Authorization", "Bearer " + adminToken)).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/health").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        // env / beans / loggers must not be exposed even to an admin
        mockMvc.perform(get("/actuator/env").header("Authorization", "Bearer " + adminToken)).andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- springdoc compatibility

    @Test
    @DisplayName("OpenAPI document is served to ADMIN (springdoc is compatible with Spring Boot 4) and refused to others")
    void openApiDocs() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/v3/api-docs").header("Authorization", "Bearer " + donorToken)).andExpect(status().isForbidden());
        mockMvc.perform(get("/v3/api-docs").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy", containsString("default-src 'self'")))
                .andExpect(jsonPath("$.openapi").value(org.hamcrest.Matchers.startsWith("3.")))
                .andExpect(jsonPath("$.info.title").value(containsString("Suwa Sarana")))
                .andExpect(jsonPath("$.paths['/api/auth/login']").exists())
                .andExpect(jsonPath("$.paths['/api/requests']").exists());
    }

    // ---------------------------------------------------------------- time

    @Test
    @DisplayName("business time uses Asia/Colombo while the JVM stays on UTC")
    void clockZone() {
        assertThat(clock.getZone()).isEqualTo(ZoneId.of("Asia/Colombo"));
        assertThat(java.util.TimeZone.getDefault().getID()).isEqualTo("UTC");
        long offsetSeconds = java.time.ZonedDateTime.now(clock).getOffset().getTotalSeconds();
        assertThat(offsetSeconds).isEqualTo(5 * 3600 + 30 * 60);
    }
}
