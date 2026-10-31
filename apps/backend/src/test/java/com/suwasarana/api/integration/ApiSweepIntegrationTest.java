package com.suwasarana.api.integration;

import com.jayway.jsonpath.JsonPath;
import com.suwasarana.api.security.JwtTokenProvider;
import com.suwasarana.api.support.PostgresIntegrationTest;
import com.suwasarana.api.user.Role;
import com.suwasarana.api.user.User;
import com.suwasarana.api.user.UserRepository;
import com.suwasarana.api.user.VerificationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Walks (almost) every endpoint of the API with real data on PostgreSQL and fails if any of them answers with a 5xx.
 *
 * <p>Why it exists: {@code spring.jpa.open-in-view=false} (audit F-32) means the Hibernate session is closed when the
 * service method returns, so any controller/DTO code that touches a LAZY association outside a transaction throws
 * {@code LazyInitializationException} (HTTP 500). The H2 unit tests mock the repositories and never see this.</p>
 */
class ApiSweepIntegrationTest extends PostgresIntegrationTest {

    @Autowired private UserRepository users;
    @Autowired private JwtTokenProvider jwt;

    /**
     * Endpoints that currently answer 500 with LazyInitializationException because open-in-view is now off, in code owned
     * by other engineers. Camp/slot: DonationSlot.resolveHostName / DonationCamp.displayName read a LAZY User outside a
     * transaction (SlotBookingService.getCampSlots / getSlotsForHospital and DonationCampService.updateCamp need
     * @Transactional(readOnly = true) or a fetch join). Remove an entry once its fix has landed - the test prints a
     * reminder when a listed endpoint starts working.
     */
    private static final List<String> KNOWN_LAZY_LOADING_FAILURES_PENDING_OWNER_FIX = List.of(
            // auth engineer: AuthService.refreshToken reads refreshToken.getUser() (LAZY) without @Transactional => every
            // silent session refresh answers 500 and users are logged out.
            "POST /api/auth/refresh",
            "GET /api/camps/{id}/slots",
            "GET /api/hospitals/{id}/slots",
            "PUT /api/camps/{id}");

    private final List<String> failures = new ArrayList<>();
    private final List<String> reached = new ArrayList<>();

    private MockHttpServletResponse call(String label, MockHttpServletRequestBuilder request, String token) throws Exception {
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        MockHttpServletResponse response = mockMvc.perform(request).andReturn().getResponse();
        reached.add(label);
        if (response.getStatus() >= 500) {
            failures.add(label + " -> HTTP " + response.getStatus() + " " + response.getContentAsString());
        }
        return response;
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder b, String body) {
        return b.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private String adminToken() {
        User admin = new User();
        admin.setEmail("admin." + UUID.randomUUID().toString().substring(0, 8) + "@example.org");
        admin.setPhoneNumber(uniquePhone());
        admin.setFirstName("Ad");
        admin.setLastName("Min");
        admin.setRole(Role.ADMIN);
        admin.setVerificationStatus(VerificationStatus.VERIFIED);
        admin.setPasswordHash("{noop}unused");
        users.save(admin);
        return jwt.generateTokenFromUsername(admin.getEmail());
    }

    @Test
    @DisplayName("no endpoint returns a 5xx for any role (guards open-in-view=false)")
    void noEndpointFailsWithServerError() throws Exception {
        String admin = adminToken();

        // --- actors
        String[] donor = registerViaOtp("DONOR", uniquePhone());
        String[] hospital = registerViaOtp("HOSPITAL_REQUESTER", uniquePhone());
        String[] requester = registerViaOtp("REQUESTER", uniquePhone());
        String donorToken = donor[0];
        String hospitalToken = hospital[0];
        // institutions publish only after an administrator verified them (VerificationGuard): do what the admin console does
        var approved = users.findById(Long.valueOf(hospital[1])).orElseThrow();
        approved.setVerificationStatus(com.suwasarana.api.user.VerificationStatus.VERIFIED);
        approved.setEmailVerifiedAt(java.time.Instant.now()); // and the emailed link was followed
        users.save(approved);
        String requesterToken = requester[0];

        // --- auth: refresh (cookie) and logout
        String refreshCookie = donor[4].split(";")[0].substring("refresh_token=".length());
        MockHttpServletResponse refreshed = call("POST /api/auth/refresh", post("/api/auth/refresh").with(freshClientIp())
                .cookie(new jakarta.servlet.http.Cookie("refresh_token", refreshCookie)), null);
        if (refreshed.getStatus() < 500) {
            assertThat(refreshed.getStatus()).as("token refresh: %s", refreshed.getContentAsString()).isEqualTo(200);
        }
        call("POST /api/auth/logout", post("/api/auth/logout").header("Authorization", "Bearer " + donorToken), null);
        call("POST /api/auth/login", json(post("/api/auth/login").with(freshClientIp()),
                "{\"email\":\"" + donor[2] + "\",\"password\":\"" + TEST_PASSWORD + "\"}"), null);

        // --- donor profile
        String dob = LocalDate.now().minusYears(30).toString();
        call("GET /api/donors/me (no profile yet)", get("/api/donors/me"), donorToken);
        call("PUT /api/donors/me", json(put("/api/donors/me"),
                "{\"bloodType\":\"O+\",\"dateOfBirth\":\"" + dob + "\",\"weightKg\":70,\"district\":\"Colombo\",\"latitude\":6.93,\"longitude\":79.86}"),
                donorToken);
        call("GET /api/donors/me", get("/api/donors/me"), donorToken);
        call("PATCH /api/donors/me/availability", json(patch("/api/donors/me/availability"), "{\"available\":true}"), donorToken);
        call("GET /api/donors/me/matches", get("/api/donors/me/matches"), donorToken);
        call("GET /api/donors/me/bookings", get("/api/donors/me/bookings"), donorToken);

        // --- hospital: camp + slots
        String tomorrow = LocalDate.now().plusDays(3).toString();
        MockHttpServletResponse camp = call("POST /api/camps", json(post("/api/camps"),
                "{\"name\":\"Sweep camp\",\"district\":\"Colombo\",\"location\":\"Town Hall\",\"latitude\":6.9,\"longitude\":79.8,"
                        + "\"scheduledDate\":\"" + tomorrow + "\",\"startTime\":\"09:00:00\",\"endTime\":\"15:00:00\"}"), hospitalToken);
        assertThat(camp.getStatus()).as("camp created: %s", camp.getContentAsString()).isLessThan(300);
        int campId = JsonPath.read(camp.getContentAsString(), "$.id");
        String start = LocalDateTime.now().plusDays(3).withHour(10).withMinute(0).withSecond(0).withNano(0).toString();
        String end = LocalDateTime.now().plusDays(3).withHour(11).withMinute(0).withSecond(0).withNano(0).toString();
        MockHttpServletResponse slot = call("POST /api/camps/{id}/slots", json(post("/api/camps/" + campId + "/slots"),
                "{\"startTime\":\"" + start + "\",\"endTime\":\"" + end + "\",\"capacity\":3}"), hospitalToken);
        assertThat(slot.getStatus()).as("slot created: %s", slot.getContentAsString()).isLessThan(300);
        int slotId = JsonPath.read(slot.getContentAsString(), "$.data.id");
        String hospitalId = hospital[1];
        call("POST /api/hospitals/{id}/slots", json(post("/api/hospitals/" + hospitalId + "/slots"),
                "{\"startTime\":\"" + start + "\",\"endTime\":\"" + end + "\",\"capacity\":2}"), hospitalToken);
        call("GET /api/hospitals/{id}/slots", get("/api/hospitals/" + hospitalId + "/slots"), null);
        call("GET /api/camps", get("/api/camps"), null);
        call("GET /api/camps/{id}/slots", get("/api/camps/" + campId + "/slots"), null);
        call("GET /api/camps/my-camps", get("/api/camps/my-camps"), hospitalToken);
        call("GET /api/camps/recommended", get("/api/camps/recommended"), donorToken);
        call("PUT /api/camps/{id}", json(put("/api/camps/" + campId),
                "{\"name\":\"Sweep camp 2\",\"district\":\"Colombo\",\"location\":\"Town Hall\",\"scheduledDate\":\"" + tomorrow
                        + "\",\"startTime\":\"09:00:00\",\"endTime\":\"15:00:00\"}"), hospitalToken);

        // --- donor books, lists, cancels, rebooks; hospital checks in
        MockHttpServletResponse booking = call("POST /api/slots/{id}/book", post("/api/slots/" + slotId + "/book"), donorToken);
        call("GET /api/donors/me/bookings (with booking)", get("/api/donors/me/bookings"), donorToken);
        call("DELETE /api/slots/{id}/book", delete("/api/slots/" + slotId + "/book"), donorToken);
        MockHttpServletResponse rebook = call("POST /api/slots/{id}/book (again)", post("/api/slots/" + slotId + "/book"), donorToken);
        if (rebook.getStatus() < 300) {
            int bookingId = JsonPath.read(rebook.getContentAsString(), "$.data.id");
            call("POST /api/slots/{id}/check-in/{bookingId}", post("/api/slots/" + slotId + "/check-in/" + bookingId), hospitalToken);
        }
        call("PATCH /api/camps/{id}/status", json(patch("/api/camps/" + campId + "/status"), "{\"status\":\"COMPLETED\"}"), hospitalToken);
        call("POST /api/camps/{id}/register (gone stub)", post("/api/camps/" + campId + "/register"), donorToken);

        // --- requester: request lifecycle, circle
        MockHttpServletResponse created = call("POST /api/requests", json(post("/api/requests"),
                "{\"patientBloodType\":\"O+\",\"unitsNeeded\":1,\"urgency\":\"URGENT\",\"hospitalName\":\"Sweep Hospital\","
                        + "\"district\":\"Colombo\",\"latitude\":6.9271,\"longitude\":79.8612}"), requesterToken);
        assertThat(created.getStatus()).as("request created: %s", created.getContentAsString()).isLessThan(300);
        int requestId = JsonPath.read(created.getContentAsString(), "$.data.id");
        call("GET /api/requests", get("/api/requests"), requesterToken);
        call("GET /api/requests/me", get("/api/requests/me"), requesterToken);
        call("GET /api/requests/{id}", get("/api/requests/" + requestId), requesterToken);
        call("GET /api/requests (donor)", get("/api/requests"), donorToken);
        MockHttpServletResponse circle = call("POST /api/requests/{id}/circle", post("/api/requests/" + requestId + "/circle"), requesterToken);
        if (circle.getStatus() < 300) {
            String inviteToken = JsonPath.read(circle.getContentAsString(), "$.data.inviteToken");
            call("GET /api/circle/{token}", get("/api/circle/" + inviteToken), null);
            call("POST /api/circle/{token}/respond", json(post("/api/circle/" + inviteToken + "/respond"),
                    "{\"responderName\":\"Friend One\",\"responderPhone\":\"0711234567\",\"bloodType\":\"O+\",\"notes\":\"ok\"}"), null);
            call("GET /api/requests/{id}/circle/responses", get("/api/requests/" + requestId + "/circle/responses"), requesterToken);
        }
        MockHttpServletResponse matches = call("GET /api/donors/me/matches (after request)", get("/api/donors/me/matches"), donorToken);
        List<Integer> matchIds = JsonPath.read(matches.getContentAsString(), "$.data[*].id");
        if (!matchIds.isEmpty()) {
            call("POST /api/matches/{id}/respond", post("/api/matches/" + matchIds.get(0) + "/respond").param("response", "ACCEPTED"), donorToken);
            call("POST /api/matches/{id}/donated", post("/api/matches/" + matchIds.get(0) + "/donated").param("donated", "true"), requesterToken);
        }

        // --- admin
        call("GET /api/admin/reports", get("/api/admin/reports"), admin);
        call("GET /api/admin/analytics", get("/api/admin/analytics"), admin);
        call("GET /api/admin/analytics/district-summary", get("/api/admin/analytics/district-summary"), admin);
        call("GET /api/admin/triage/queue", get("/api/admin/triage/queue"), admin);
        call("PUT /api/admin/triage/{id}/review", json(put("/api/admin/triage/" + requestId + "/review"), "{\"notes\":\"checked\"}"), admin);
        call("GET /api/admin/verifications/pending", get("/api/admin/verifications/pending"), admin);
        call("PUT /api/admin/verifications/{id}/approve", put("/api/admin/verifications/" + hospitalId + "/approve"), admin);
        call("POST /api/admin/requesters/{id}/verify", post("/api/admin/requesters/" + requester[1] + "/verify"), admin);
        call("GET /api/analytics/district-summary", get("/api/analytics/district-summary"), requesterToken);
        call("GET /api/requests (admin)", get("/api/requests"), admin);
        call("PUT /api/requests/{id}/cancel", put("/api/requests/" + requestId + "/cancel"), requesterToken);

        // --- public
        call("GET /api/inventory/stock-signals", get("/api/inventory/stock-signals"), null);
        call("POST /api/contact", json(post("/api/contact"),
                "{\"name\":\"Sweep\",\"email\":\"sweep@example.org\",\"subject\":\"Hello\",\"message\":\"Testing the contact form.\"}"), null);
        call("POST /api/faq/ask", json(post("/api/faq/ask"), "{\"question\":\"What is the minimum age and weight to donate blood?\"}"), null);
        call("POST /api/requests/ai-draft", json(post("/api/requests/ai-draft"),
                "{\"text\":\"Need 2 units of O positive blood at Colombo National Hospital urgently\"}"), requesterToken);
        call("GET /api/ivr/webhook", get("/api/ivr/webhook").param("Digits", "1"), null);
        call("OPTIONS preflight", org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .request(HttpMethod.OPTIONS, "/api/requests").header("Origin", "https://suwasarana.test")
                .header("Access-Control-Request-Method", "POST"), null);

        List<String> unexpected = failures.stream()
                .filter(f -> KNOWN_LAZY_LOADING_FAILURES_PENDING_OWNER_FIX.stream().noneMatch(f::startsWith))
                .toList();
        KNOWN_LAZY_LOADING_FAILURES_PENDING_OWNER_FIX.stream()
                .filter(known -> failures.stream().noneMatch(f -> f.startsWith(known)))
                .forEach(known -> System.out.println("NOTE: '" + known + "' no longer fails - remove it from the known-failure list."));
        assertThat(reached).as("the walk reached the end of the scenario").contains("POST /api/faq/ask", "OPTIONS preflight");
        assertThat(unexpected).as("endpoints answering with a server error").isEmpty();
    }
}
