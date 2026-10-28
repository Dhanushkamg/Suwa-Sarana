package com.suwasarana.api.integration;

import com.jayway.jsonpath.JsonPath;
import com.suwasarana.api.support.PostgresIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end API flow on the real stack (PostgreSQL + Flyway schema + security chain), driven over HTTP-level MockMvc:
 *
 * <pre>
 *   donor A (Colombo) and donor B (Kandy):  send-otp -> verify-otp -> register -> complete profile
 *   requester:                              send-otp -> verify-otp -> register -> login
 *   requester creates an URGENT O+ request in Colombo -> matching engine (native earthdistance SQL) runs
 *   => donor A (inside the radius) receives a PENDING match, donor B (about 100 km away) does not
 *   donor A accepts -> the single needed unit is covered -> the request is MATCHED
 * </pre>
 *
 * The OTP is obtained "the way a phone would": from the SMS handed to the (spied) SMS gateway, so the test keeps
 * working however the application stores OTPs. Each call uses its own client address because the auth endpoints are
 * rate limited per IP.
 */
class EndToEndIntegrationTest extends PostgresIntegrationTest {

    private String body(String template, Object... args) {
        return template.formatted(args);
    }

    private void saveDonorProfile(String token, String bloodType, String district, double lat, double lon) throws Exception {
        String dob = LocalDate.now().minusYears(28).toString();
        mockMvc.perform(put("/api/donors/me").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("""
                                {"bloodType":"%s","dateOfBirth":"%s","weightKg":70.5,"district":"%s",
                                 "latitude":%s,"longitude":%s}""", bloodType, dob, district, lat, lon)))
                .andExpect(status().isOk())
                // dates are ISO strings, never [yyyy, m, d] arrays
                .andExpect(jsonPath("$.data.dateOfBirth").value(dob));
    }

    @Test
    @DisplayName("register (OTP) -> login -> create request -> geo matching -> donor accepts")
    void fullFlow() throws Exception {
        // --- two donors with different locations
        String[] donorColombo = registerViaOtp("DONOR", uniquePhone());
        saveDonorProfile(donorColombo[0], "O+", "Colombo", 6.9275, 79.8615);
        String[] donorKandy = registerViaOtp("DONOR", uniquePhone());
        saveDonorProfile(donorKandy[0], "O+", "Kandy", 7.2906, 80.6337);

        // --- requester registers, then signs in with e-mail + password
        String requesterPhone = uniquePhone();
        String[] requester = registerViaOtp("REQUESTER", requesterPhone);
        String loginResponse = mockMvc.perform(post("/api/auth/login").with(freshClientIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("{\"email\":\"%s\",\"password\":\"%s\"}", requester[2], TEST_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("REQUESTER"))
                .andReturn().getResponse().getContentAsString();
        String requesterToken = JsonPath.read(loginResponse, "$.data.accessToken");

        // --- create an URGENT request in Colombo (CRITICAL would additionally need admin verification)
        String created = mockMvc.perform(post("/api/requests").header("Authorization", "Bearer " + requesterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"patientBloodType":"O+","unitsNeeded":1,"urgency":"URGENT",
                                 "hospitalName":"National Hospital Colombo","district":"Colombo",
                                 "latitude":6.9271,"longitude":79.8612}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(org.hamcrest.Matchers.anyOf(
                        org.hamcrest.Matchers.is("OPEN"), org.hamcrest.Matchers.is("ESCALATING"))))
                // timestamps are ISO-8601 strings, never numeric arrays
                .andExpect(jsonPath("$.data.createdAt").isString())
                .andExpect(jsonPath("$.timestamp").isString())
                .andReturn().getResponse().getContentAsString();
        int requestId = JsonPath.read(created, "$.data.id");

        // --- the nearby donor was matched, the distant one was not
        String nearby = mockMvc.perform(get("/api/donors/me/matches").header("Authorization", "Bearer " + donorColombo[0]))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Integer> nearbyRequestIds = JsonPath.read(nearby, "$.data[*].request.id");
        assertThat(nearbyRequestIds).contains(requestId);
        List<Integer> matchIds = JsonPath.read(nearby, "$.data[?(@.request.id == " + requestId + ")].id");
        List<String> matchStatuses = JsonPath.read(nearby, "$.data[?(@.request.id == " + requestId + ")].status");
        int matchId = matchIds.get(0);
        assertThat(matchStatuses).containsExactly("PENDING");
        assertThat((String) JsonPath.read(nearby, "$.data[0].createdAt")).contains("T");

        mockMvc.perform(get("/api/donors/me/matches").header("Authorization", "Bearer " + donorKandy[0]))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.request.id == " + requestId + ")]").isEmpty());

        // --- the donor accepts; the single requested unit is now covered
        mockMvc.perform(post("/api/matches/" + matchId + "/respond").param("response", "ACCEPTED")
                        .header("Authorization", "Bearer " + donorColombo[0]))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/requests/me").header("Authorization", "Bearer " + requesterToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == " + requestId + ")].status").value(org.hamcrest.Matchers.contains("MATCHED")));
    }

    @Test
    @DisplayName("unauthenticated and wrong-role calls get the JSON error envelope with codes")
    void errorEnvelope() throws Exception {
        mockMvc.perform(get("/api/requests/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("auth.unauthorized"));

        String[] donor = registerViaOtp("DONOR", uniquePhone());
        mockMvc.perform(get("/api/admin/reports").header("Authorization", "Bearer " + donor[0]))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("auth.forbidden"));

        mockMvc.perform(post("/api/auth/login").with(freshClientIp())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"))
                .andExpect(jsonPath("$.data.password").exists());
    }
}
