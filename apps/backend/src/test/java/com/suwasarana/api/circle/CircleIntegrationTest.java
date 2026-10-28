package com.suwasarana.api.circle;

import com.suwasarana.api.circle.dto.CircleInviteDto;
import com.suwasarana.api.ratelimit.RateLimitService;
import com.suwasarana.api.request.BloodRequest;
import com.suwasarana.api.request.RequestRepository;
import com.suwasarana.api.request.RequestStatus;
import com.suwasarana.api.request.Urgency;
import com.suwasarana.api.user.Role;
import com.suwasarana.api.user.User;
import com.suwasarana.api.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Circle invite tokens and pledges through the real HTTP stack (H2). */
@SpringBootTest
@ActiveProfiles("test")
class CircleIntegrationTest {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository userRepository;
    @Autowired private RequestRepository requestRepository;
    @Autowired private CircleService circleService;
    @Autowired private CircleResponseRepository responseRepository;
    @Autowired private RequestCircleRepository circleRepository;
    @Autowired private CircleTokens tokens;
    @Autowired private CircleTokenMigrator migrator;
    @Autowired private RateLimitService limits;
    @Autowired private JdbcTemplate jdbc;

    private MockMvc mvc;
    private User owner;
    private BloodRequest request;
    private String token;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        limits.store().clear();

        String unique = UUID.randomUUID().toString().substring(0, 8);
        owner = new User();
        owner.setEmail("owner-" + unique + "@test.com");
        owner.setPhoneNumber("07" + Math.abs(unique.hashCode() % 100_000_000));
        owner.setFirstName("Owner");
        owner.setLastName("Test");
        owner.setPasswordHash("x");
        owner.setRole(Role.REQUESTER);
        owner = userRepository.save(owner);

        request = new BloodRequest();
        request.setRequester(owner);
        request.setPatientBloodType("O+");
        request.setUnitsNeeded((short) 2);
        request.setUrgency(Urgency.CRITICAL);
        request.setHospitalName("Galle Teaching Hospital");
        request.setDistrict("Galle");
        request.setLatitude(6.03);
        request.setLongitude(80.2);
        request.setStatus(RequestStatus.OPEN);
        request.setExpiresAt(LocalDateTime.now().plusHours(12));
        request = requestRepository.save(request);

        CircleInviteDto invite = circleService.createOrGetCircle(request.getId(), owner.getId());
        token = invite.getInviteToken();
    }

    private long pledgeCount() {
        return circleRepository.findByRequestId(request.getId())
                .map(c -> responseRepository.countByCircleId(c.getId())).orElse(0L);
    }

    private static String pledgeJson(String phone) {
        return "{\"responderName\":\"Kasun Perera\",\"responderPhone\":\"" + phone + "\",\"bloodType\":\"O+\",\"notes\":\"Free after 5pm\"}";
    }

    @Test
    @DisplayName("The database holds neither the token nor anything it can be read from without the key")
    void tokenNotStoredInClear() {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM request_circles WHERE request_id = ?", request.getId());
        assertEquals(1, rows.size());
        Map<String, Object> row = rows.get(0);
        for (Object value : row.values()) {
            assertFalse(String.valueOf(value).contains(token), "a column contains the clear token");
        }
        assertEquals(tokens.hash(token), row.get("invite_token_hash"));
        assertTrue(String.valueOf(row.get("invite_token_enc")).startsWith("v1:"));
    }

    @Test
    @DisplayName("Public info works with the token and never returns private owner data")
    void publicInfo() throws Exception {
        mvc.perform(get("/api/circle/" + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.patientBloodType").value("O+"))
                .andExpect(jsonPath("$.data.hospitalName").value("Galle Teaching Hospital"));
    }

    @Test
    @DisplayName("Unknown token: 404 whose body does not echo the token")
    void unknownTokenIsNotEchoed() throws Exception {
        String bogus = "f".repeat(64);
        String body = mvc.perform(get("/api/circle/" + bogus)).andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        assertFalse(body.contains(bogus));
        String body2 = mvc.perform(post("/api/circle/" + bogus + "/respond").contentType(JSON).content(pledgeJson("0771234567")))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        assertFalse(body2.contains(bogus));
    }

    @Test
    @DisplayName("After the request is cancelled / fulfilled / expired the link is gone (410) and nothing is echoed")
    void linkDiesWithRequest() throws Exception {
        for (RequestStatus closed : new RequestStatus[] {RequestStatus.CANCELLED, RequestStatus.FULFILLED, RequestStatus.EXPIRED}) {
            request.setStatus(closed);
            requestRepository.save(request);
            String info = mvc.perform(get("/api/circle/" + token)).andExpect(status().isGone())
                    .andReturn().getResponse().getContentAsString();
            String respond = mvc.perform(post("/api/circle/" + token + "/respond").contentType(JSON).content(pledgeJson("0771234567")))
                    .andExpect(status().isGone()).andReturn().getResponse().getContentAsString();
            assertFalse(info.contains(token), closed + " info");
            assertFalse(respond.contains(token), closed + " respond");
        }
        assertEquals(0, pledgeCount());
        request.setStatus(RequestStatus.OPEN);
        requestRepository.save(request);
        mvc.perform(get("/api/circle/" + token)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("A pledge is saved and the requester gets an in-app notification (inbox), which the answer reports honestly")
    void pledgeHonestAboutNotification() throws Exception {
        mvc.perform(post("/api/circle/" + token + "/respond").contentType(JSON).content(pledgeJson("077 123 4567")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.responderPhone").value("+94771234567"))
                .andExpect(jsonPath("$.data.requesterNotified").value(true))
                .andExpect(jsonPath("$.message").isNotEmpty());
        assertEquals(1, pledgeCount());
    }

    @Test
    @DisplayName("Duplicate phone 409, invalid phone 400, short name 400")
    void pledgeValidation() throws Exception {
        mvc.perform(post("/api/circle/" + token + "/respond").contentType(JSON).content(pledgeJson("0771234567")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/circle/" + token + "/respond").contentType(JSON).content(pledgeJson("+94 77 123 4567")))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/circle/" + token + "/respond").contentType(JSON).content(pledgeJson("0000000000")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/circle/" + token + "/respond").contentType(JSON).content(pledgeJson("12345678901234")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/circle/" + token + "/respond").contentType(JSON)
                        .content(pledgeJson("0779876543").replace("Kasun Perera", "K")))
                .andExpect(status().isBadRequest());
        assertEquals(1, pledgeCount());
    }

    @Test
    @DisplayName("Legacy plaintext tokens are converted at start-up: still valid, encrypted copy readable, plaintext erased")
    void legacyTokensAreConverted() {
        jdbc.execute("ALTER TABLE request_circles ADD COLUMN IF NOT EXISTS invite_token VARCHAR(64)");
        String legacy = "legacy-token-" + UUID.randomUUID();
        // the state V40 leaves behind: hash backfilled, no encrypted copy yet, plaintext still present
        BloodRequest other = new BloodRequest();
        other.setRequester(owner);
        other.setPatientBloodType("A-");
        other.setUnitsNeeded((short) 1);
        other.setUrgency(Urgency.URGENT);
        other.setHospitalName("Matara Hospital");
        other.setDistrict("Matara");
        other.setLatitude(5.9);
        other.setLongitude(80.5);
        other.setExpiresAt(LocalDateTime.now().plusHours(5));
        other = requestRepository.save(other);
        jdbc.update("INSERT INTO request_circles (request_id, invite_token_hash, invite_token, created_at, expires_at) VALUES (?,?,?,?,?)",
                other.getId(), tokens.hash(legacy), legacy, LocalDateTime.now(), LocalDateTime.now().plusHours(5));

        int converted = migrator.convertLegacyTokens();

        assertTrue(converted >= 1);
        Map<String, Object> row = jdbc.queryForMap("SELECT invite_token, invite_token_enc FROM request_circles WHERE request_id = ?", other.getId());
        assertNull(row.get("invite_token"));
        assertEquals(legacy, tokens.decrypt((String) row.get("invite_token_enc")).orElseThrow());
        assertTrue(circleRepository.findByInviteTokenHash(tokens.hash(legacy)).isPresent(), "the old link keeps working");
        assertEquals(0, migrator.convertLegacyTokens(), "second run is a no-op");
    }
}
