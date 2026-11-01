package com.suwasarana.api.security;

import com.suwasarana.api.support.IntegrationTestBase;
import com.suwasarana.api.user.Role;
import com.suwasarana.api.user.User;
import com.suwasarana.api.user.VerificationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Data-driven role matrix over EVERY endpoint (docs/audit/backend.md section 1 plus the new auth / admin / privacy ones).
 *
 * For each endpoint and each actor (anonymous, DONOR, REQUESTER, HOSPITAL_REQUESTER, BLOOD_BANK_REQUESTER, ADMIN):
 *  - an actor that is allowed must NOT be answered 401/403;
 *  - anonymous callers of a protected endpoint get 401, signed-in callers with the wrong role get 403.
 * Requests go through the full security filter chain with real JWTs, so URL rules, JwtAuthFilter and @PreAuthorize are all
 * exercised (no direct controller calls). A coverage check fails when a mapped endpoint is missing from the matrix.
 */
class RoleMatrixTest extends IntegrationTestBase {

    static final String ANON = "ANON";
    static final String DONOR = "DONOR";
    static final String REQUESTER = "REQUESTER";
    static final String HOSPITAL = "HOSPITAL_REQUESTER";
    static final String BANK = "BLOOD_BANK_REQUESTER";
    static final String ADMIN = "ADMIN";

    static final List<String> ACTORS = List.of(ANON, DONOR, REQUESTER, HOSPITAL, BANK, ADMIN);
    static final Set<String> EVERYONE = Set.of(ANON, DONOR, REQUESTER, HOSPITAL, BANK, ADMIN);
    static final Set<String> SIGNED_IN = Set.of(DONOR, REQUESTER, HOSPITAL, BANK, ADMIN);
    static final Set<String> ONLY_DONOR = Set.of(DONOR);
    static final Set<String> ONLY_ADMIN = Set.of(ADMIN);
    static final Set<String> INSTITUTIONS = Set.of(HOSPITAL, BANK);
    static final Set<String> INSTITUTIONS_AND_ADMIN = Set.of(HOSPITAL, BANK, ADMIN);
    static final Set<String> REQUEST_CREATORS = Set.of(REQUESTER, HOSPITAL, ADMIN);

    /** One endpoint of the matrix. {@code roleIndependent}: the business answer is a fixed 401/403 for everybody (cookie / signature checks), so only "same for all actors" is asserted. */
    record Ep(HttpMethod method, String path, String body, Set<String> allowed, boolean roleIndependent, Set<String> businessForbidden) {
        Ep(HttpMethod method, String path, String body, Set<String> allowed) {
            this(method, path, body, allowed, false, Set.of());
        }

        String template() {
            return path.replaceAll("\\?.*$", "").replaceAll("/[0-9]{3,}(?=/|$)", "/{}").replaceAll("/sample-token(?=/|$)", "/{}");
        }

        @Override
        public String toString() {
            return method + " " + path;
        }
    }

    private static Ep ep(HttpMethod m, String path, Set<String> allowed) {
        return new Ep(m, path, null, allowed);
    }

    private static Ep ep(HttpMethod m, String path, String body, Set<String> allowed) {
        return new Ep(m, path, body, allowed);
    }

    static final List<Ep> MATRIX = List.of(
            // ---- auth: public by design ----------------------------------------------------------------------------
            ep(HttpMethod.POST, "/api/auth/register", "{}", EVERYONE),
            ep(HttpMethod.POST, "/api/auth/login", "{}", EVERYONE),
            ep(HttpMethod.POST, "/api/auth/send-otp", "{}", EVERYONE),
            ep(HttpMethod.POST, "/api/auth/verify-otp", "{}", EVERYONE),
            ep(HttpMethod.POST, "/api/auth/reset-password", "{}", EVERYONE),
            ep(HttpMethod.POST, "/api/auth/forgot-password", "{}", EVERYONE),
            ep(HttpMethod.POST, "/api/auth/reset-password/token", "{}", EVERYONE),
            ep(HttpMethod.POST, "/api/auth/verify-email", "{}", EVERYONE),
            ep(HttpMethod.POST, "/api/auth/accept-invite", "{}", EVERYONE),
            ep(HttpMethod.POST, "/api/auth/google/verify", "{}", EVERYONE),
            ep(HttpMethod.POST, "/api/auth/google/link", "{}", EVERYONE),
            ep(HttpMethod.POST, "/api/auth/google/register", "{}", EVERYONE),
            ep(HttpMethod.POST, "/api/auth/logout", EVERYONE),
            new Ep(HttpMethod.POST, "/api/auth/refresh", null, EVERYONE, true, Set.of()),
            ep(HttpMethod.POST, "/api/auth/resend-verification", SIGNED_IN),

            // ---- my account (any signed-in user) -------------------------------------------------------------------
            ep(HttpMethod.GET, "/api/users/me", SIGNED_IN),
            ep(HttpMethod.PATCH, "/api/users/me", "{}", SIGNED_IN),
            ep(HttpMethod.POST, "/api/users/me/change-password", "{}", SIGNED_IN),
            ep(HttpMethod.POST, "/api/users/me/consent", SIGNED_IN),
            ep(HttpMethod.POST, "/api/users/me/send-otp", SIGNED_IN),
            ep(HttpMethod.GET, "/api/users/me/export", SIGNED_IN),
            // erasure is self-service for everyone except administrators (a 403 from the business rule, not from security)
            new Ep(HttpMethod.DELETE, "/api/users/me", "{}", SIGNED_IN, false, Set.of(ADMIN)),

            // ---- blood requests ------------------------------------------------------------------------------------
            ep(HttpMethod.POST, "/api/requests", "{}", REQUEST_CREATORS),
            ep(HttpMethod.POST, "/api/requests/ai-draft", "{}", REQUEST_CREATORS),
            ep(HttpMethod.GET, "/api/requests", SIGNED_IN),
            ep(HttpMethod.GET, "/api/requests/me", REQUEST_CREATORS),
            ep(HttpMethod.GET, "/api/requests/999001", SIGNED_IN),
            ep(HttpMethod.PUT, "/api/requests/999001/cancel", REQUEST_CREATORS),
            ep(HttpMethod.GET, "/api/requests/999001/matches", REQUEST_CREATORS),
            ep(HttpMethod.POST, "/api/requests/999001/fulfil", REQUEST_CREATORS),
            ep(HttpMethod.POST, "/api/requests/999001/close", "{}", REQUEST_CREATORS),
            // any signed-in user may report a request (once per request)
            ep(HttpMethod.POST, "/api/requests/999001/report", "{}", SIGNED_IN),

            // ---- circles ------------------------------------------------------------------------------------------------
            ep(HttpMethod.POST, "/api/requests/999001/circle", SIGNED_IN),
            ep(HttpMethod.GET, "/api/requests/999001/circle/responses", SIGNED_IN),
            ep(HttpMethod.GET, "/api/circle/sample-token", EVERYONE),
            ep(HttpMethod.POST, "/api/circle/sample-token/respond", "{}", EVERYONE),

            // ---- matches / donors ------------------------------------------------------------------------------------
            ep(HttpMethod.POST, "/api/matches/999001/respond?response=ACCEPTED", ONLY_DONOR),
            ep(HttpMethod.POST, "/api/matches/999001/donated?donated=true", REQUEST_CREATORS),
            ep(HttpMethod.GET, "/api/donors/me", ONLY_DONOR),
            ep(HttpMethod.PUT, "/api/donors/me", "{}", ONLY_DONOR),
            ep(HttpMethod.PATCH, "/api/donors/me/availability", "{}", ONLY_DONOR),
            ep(HttpMethod.GET, "/api/donors/me/matches", ONLY_DONOR),
            ep(HttpMethod.GET, "/api/donors/me/bookings", ONLY_DONOR),
            ep(HttpMethod.GET, "/api/donors/me/donations", ONLY_DONOR),
            ep(HttpMethod.GET, "/api/donors/me/eligibility", ONLY_DONOR),

            // ---- camps / slots ------------------------------------------------------------------------------------------
            ep(HttpMethod.GET, "/api/camps", EVERYONE),
            ep(HttpMethod.GET, "/api/camps/recommended", ONLY_DONOR),
            ep(HttpMethod.GET, "/api/camps/my-camps", INSTITUTIONS),
            ep(HttpMethod.POST, "/api/camps", "{}", INSTITUTIONS),
            ep(HttpMethod.PUT, "/api/camps/999001", "{}", INSTITUTIONS),
            ep(HttpMethod.PATCH, "/api/camps/999001/status", "{\"status\":\"CANCELLED\"}", INSTITUTIONS),
            ep(HttpMethod.POST, "/api/camps/999001/register", ONLY_DONOR),
            ep(HttpMethod.GET, "/api/camps/999001/slots", EVERYONE),
            ep(HttpMethod.POST, "/api/camps/999001/slots", "{}", INSTITUTIONS_AND_ADMIN),
            ep(HttpMethod.POST, "/api/camps/999001/slots/bulk", "{}", INSTITUTIONS_AND_ADMIN),
            ep(HttpMethod.GET, "/api/hospitals/999001/slots", EVERYONE),
            ep(HttpMethod.POST, "/api/hospitals/999001/slots", "{}", INSTITUTIONS_AND_ADMIN),
            ep(HttpMethod.POST, "/api/hospitals/999001/slots/bulk", "{}", INSTITUTIONS_AND_ADMIN),
            ep(HttpMethod.POST, "/api/slots/999001/book", ONLY_DONOR),
            ep(HttpMethod.DELETE, "/api/slots/999001/book", ONLY_DONOR),
            ep(HttpMethod.POST, "/api/slots/999001/check-in/999002", INSTITUTIONS_AND_ADMIN),
            ep(HttpMethod.GET, "/api/slots/999001/bookings", INSTITUTIONS_AND_ADMIN),

            // ---- blood-bank stock (class-level @PreAuthorize: hospitals and blood banks only, not even ADMIN) -------------
            ep(HttpMethod.GET, "/api/blood-bank/stock", INSTITUTIONS),
            ep(HttpMethod.POST, "/api/blood-bank/stock", "{}", INSTITUTIONS),
            ep(HttpMethod.PUT, "/api/blood-bank/stock/999001", "{}", INSTITUTIONS),
            ep(HttpMethod.DELETE, "/api/blood-bank/stock/999001", INSTITUTIONS),
            ep(HttpMethod.POST, "/api/blood-bank/stock/issue", "{}", INSTITUTIONS),
            ep(HttpMethod.POST, "/api/blood-bank/stock/confirm", INSTITUTIONS),
            ep(HttpMethod.GET, "/api/blood-bank/stock/movements", INSTITUTIONS),
            ep(HttpMethod.GET, "/api/blood-bank/thresholds", INSTITUTIONS),
            ep(HttpMethod.PUT, "/api/blood-bank/thresholds", "{}", INSTITUTIONS),
            ep(HttpMethod.GET, "/api/blood-bank/site", INSTITUTIONS),
            ep(HttpMethod.PUT, "/api/blood-bank/site", "{}", INSTITUTIONS),

            // ---- notification inbox (any signed-in user) --------------------------------------------------------------
            ep(HttpMethod.GET, "/api/notifications", SIGNED_IN),
            ep(HttpMethod.GET, "/api/notifications/unread-count", SIGNED_IN),
            ep(HttpMethod.POST, "/api/notifications/999001/read", SIGNED_IN),
            ep(HttpMethod.POST, "/api/notifications/read-all", SIGNED_IN),

            // ---- exports (scoped to the caller's own data inside the controller) -----------------------------------------
            ep(HttpMethod.GET, "/api/exports/requests.csv", SIGNED_IN),
            ep(HttpMethod.GET, "/api/exports/donations.csv", SIGNED_IN),

            // ---- reference data ---------------------------------------------------------------------------------------
            ep(HttpMethod.GET, "/api/meta/districts", EVERYONE),

            // ---- admin ----------------------------------------------------------------------------------------------------
            ep(HttpMethod.GET, "/api/admin/reports", ONLY_ADMIN),
            ep(HttpMethod.GET, "/api/admin/analytics", ONLY_ADMIN),
            ep(HttpMethod.GET, "/api/admin/analytics/district-summary", ONLY_ADMIN),
            ep(HttpMethod.GET, "/api/admin/triage/queue", ONLY_ADMIN),
            ep(HttpMethod.PUT, "/api/admin/triage/999001/review", "{}", ONLY_ADMIN),
            ep(HttpMethod.GET, "/api/admin/verifications/pending", ONLY_ADMIN),
            ep(HttpMethod.GET, "/api/admin/verifications", ONLY_ADMIN),
            ep(HttpMethod.PUT, "/api/admin/verifications/999001/approve", "{}", ONLY_ADMIN),
            ep(HttpMethod.PUT, "/api/admin/verifications/999001/reject", "{}", ONLY_ADMIN),
            ep(HttpMethod.GET, "/api/admin/users", ONLY_ADMIN),
            ep(HttpMethod.POST, "/api/admin/users/999001/suspend", "{}", ONLY_ADMIN),
            ep(HttpMethod.POST, "/api/admin/users/999001/restore", "{}", ONLY_ADMIN),
            ep(HttpMethod.PUT, "/api/admin/users/999001/role", "{}", ONLY_ADMIN),
            ep(HttpMethod.GET, "/api/admin/users/999001/export?reason=matrix", ONLY_ADMIN),
            ep(HttpMethod.GET, "/api/admin/audit", ONLY_ADMIN),
            ep(HttpMethod.POST, "/api/admin/invitations", "{}", ONLY_ADMIN),
            ep(HttpMethod.GET, "/api/admin/analytics/summary", ONLY_ADMIN),
            ep(HttpMethod.GET, "/api/admin/contact-messages", ONLY_ADMIN),
            ep(HttpMethod.PATCH, "/api/admin/contact-messages/999001", "{}", ONLY_ADMIN),
            ep(HttpMethod.GET, "/api/analytics/district-summary", SIGNED_IN),
            ep(HttpMethod.GET, "/api/analytics/summary", SIGNED_IN),

            // ---- public / misc --------------------------------------------------------------------------------------------
            ep(HttpMethod.POST, "/api/faq/ask", "{}", EVERYONE),
            // poster copy: any signed-in role passes security; URGENT_REQUEST additionally needs a VERIFIED requester (422, see AiControllersIntegrationTest)
            ep(HttpMethod.POST, "/api/ai/poster-copy", "{}", SIGNED_IN),
            ep(HttpMethod.GET, "/api/inventory/stock-signals", EVERYONE),
            ep(HttpMethod.POST, "/api/contact", "{}", EVERYONE),
            // the IVR webhook is public for Twilio; whatever it answers (signature check) must not depend on the caller's role
            new Ep(HttpMethod.GET, "/api/ivr/webhook", null, EVERYONE, true, Set.of()),
            new Ep(HttpMethod.POST, "/api/ivr/webhook", null, EVERYONE, true, Set.of()),
            ep(HttpMethod.GET, "/api/notifications/stream", SIGNED_IN),

            // ---- operations -------------------------------------------------------------------------------------------------
            // liveness / readiness are public for the load balancer; the aggregate /actuator/health (details) is ADMIN only
            ep(HttpMethod.GET, "/actuator/health", ONLY_ADMIN),
            ep(HttpMethod.GET, "/actuator/health/liveness", EVERYONE),
            ep(HttpMethod.GET, "/actuator/health/readiness", EVERYONE),
            ep(HttpMethod.GET, "/actuator/env", ONLY_ADMIN),
            ep(HttpMethod.GET, "/actuator/metrics", ONLY_ADMIN),
            ep(HttpMethod.GET, "/v3/api-docs", ONLY_ADMIN),
            ep(HttpMethod.GET, "/swagger-ui/index.html", ONLY_ADMIN)
    );

    @Autowired @org.springframework.beans.factory.annotation.Qualifier("requestMappingHandlerMapping") private RequestMappingHandlerMapping handlerMapping;
    @Autowired private com.suwasarana.api.security.JwtTokenProvider tokenProvider;

    private final Map<String, String> tokens = new HashMap<>();

    private void createActors() {
        if (!tokens.isEmpty()) {
            return;
        }
        for (Role role : Role.values()) {
            String email = "matrix-" + role.name().toLowerCase() + "@example.com";
            User user = userRepository.findByEmail(email).orElseGet(() -> {
                User u = new User();
                u.setEmail(email);
                u.setPhoneNumber(uniquePhone());
                u.setFirstName("Matrix");
                u.setLastName(role.name());
                u.setRole(role);
                u.setPasswordHash(passwordEncoder.encode(STRONG_PASSWORD));
                // institutions are fully verified so that business rules (VerificationGuard) do not mask the security result
                u.setVerificationStatus(VerificationStatus.VERIFIED);
                u.setEmailVerifiedAt(Instant.now());
                return userRepository.save(u);
            });
            tokens.put(role.name(), tokenProvider.generateAccessToken(user.getEmail(), user.getTokenVersion()));
        }
    }

    static Stream<org.junit.jupiter.params.provider.Arguments> cases() {
        List<org.junit.jupiter.params.provider.Arguments> all = new ArrayList<>();
        for (Ep ep : MATRIX) {
            for (String actor : ACTORS) {
                all.add(org.junit.jupiter.params.provider.Arguments.of(ep, actor));
            }
        }
        return all.stream();
    }

    private int statusOf(Ep ep, String actor) throws Exception {
        createActors();
        var builder = MockMvcRequestBuilders.request(ep.method(), ep.path());
        builder.with(freshIp());
        if (ep.body() != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(ep.body());
        }
        if (!ANON.equals(actor)) {
            builder.header("Authorization", "Bearer " + tokens.get(actor));
        }
        MvcResult result = mvc.perform(builder).andReturn();
        return result.getResponse().getStatus();
    }

    @ParameterizedTest(name = "{0} as {1}")
    @MethodSource("cases")
    @DisplayName("role matrix")
    void endpointHonoursTheRoleMatrix(Ep ep, String actor) throws Exception {
        int status = statusOf(ep, actor);

        if (ep.roleIndependent()) {
            assertEquals(statusOf(ep, ANON), status, ep + " must answer the same way for every actor, but " + actor + " got " + status);
            return;
        }
        boolean allowed = ep.allowed().contains(actor);
        if (allowed) {
            if (ep.businessForbidden().contains(actor)) {
                return; // a documented business-rule 403 (e.g. administrators cannot self-delete); security let the request through
            }
            assertFalse(status == 401 || status == 403, ep + " must be allowed for " + actor + " but answered " + status);
        } else if (ANON.equals(actor)) {
            assertEquals(401, status, ep + " must answer anonymous callers with 401");
        } else {
            assertEquals(403, status, ep + " must answer " + actor + " with 403");
        }
    }

    @Test
    @DisplayName("every mapped endpoint is in the matrix")
    void everyEndpointIsCovered() {
        Set<String> covered = MATRIX.stream().map(e -> e.method() + " " + e.template()).collect(Collectors.toSet());
        // some path templates in the matrix use concrete ids; compare on method + normalised template
        Set<String> missing = new TreeSet<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMapping.getHandlerMethods().entrySet()) {
            RequestMappingInfo info = entry.getKey();
            if (info.getPathPatternsCondition() == null) {
                continue;
            }
            Set<String> methods = info.getMethodsCondition().getMethods().stream().map(Enum::name).collect(Collectors.toCollection(TreeSet::new));
            if (methods.isEmpty()) {
                methods = new TreeSet<>(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE")); // any method mapping
            }
            for (String pattern : info.getPathPatternsCondition().getPatternValues()) {
                if (pattern.startsWith("/error") || pattern.startsWith("/v3/api-docs") || pattern.startsWith("/swagger-ui")) {
                    continue;
                }
                String normalised = pattern.replaceAll("\\{[^/}]+}", "{}");
                boolean anyMethodCovered = false;
                for (String m : methods) {
                    if (covered.contains(m + " " + normalised.replaceAll("/[0-9]{3,}(?=/|$)", "/{}"))) {
                        anyMethodCovered = true;
                    }
                }
                if (!anyMethodCovered) {
                    missing.add(String.join("|", methods) + " " + pattern);
                }
            }
        }
        assertTrue(missing.isEmpty(), "endpoints missing from the role matrix (add them to RoleMatrixTest.MATRIX): " + missing);
    }

    @Test
    @DisplayName("the matrix itself is consistent")
    void matrixIsWellFormed() {
        Map<String, Integer> seen = new LinkedHashMap<>();
        for (Ep ep : MATRIX) {
            seen.merge(ep.method() + " " + ep.path(), 1, Integer::sum);
            assertFalse(ep.allowed().isEmpty(), ep + " has no allowed actor");
            assertTrue(EnumSet.allOf(Role.class).stream().map(Enum::name).collect(Collectors.toSet()).containsAll(
                    ep.allowed().stream().filter(a -> !ANON.equals(a)).collect(Collectors.toSet())));
        }
        assertTrue(seen.values().stream().allMatch(c -> c == 1), "duplicate rows: " + seen);
        assertTrue(MATRIX.size() >= 80, "inventory has " + MATRIX.size() + " rows");
    }

}
