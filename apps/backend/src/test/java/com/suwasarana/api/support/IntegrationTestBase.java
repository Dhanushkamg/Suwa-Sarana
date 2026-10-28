package com.suwasarana.api.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.suwasarana.api.user.Role;
import com.suwasarana.api.user.User;
import com.suwasarana.api.user.UserRepository;
import com.suwasarana.api.user.VerificationStatus;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Shared base for the auth / admin / privacy integration tests: ONE Spring context (and its own in-memory H2 database,
 * so leftovers never interfere with other test classes), with recorded SMS/email and synchronous sending.
 * Every user flow goes through the real HTTP API (security filter chain included).
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(MessagingTestConfig.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:authit;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        // application.yml pins the PostgreSQL dialect, which mis-binds Instant on H2 (shifted by the JVM zone offset). These
        // tests run on H2, so use its own dialect; the Postgres path is covered by the manual end-to-end run documented in the report.
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
public abstract class IntegrationTestBase {

    public static final String STRONG_PASSWORD = "Tr1cky-Unique-Passphrase";

    private static final AtomicInteger SEQ = new AtomicInteger(1000);
    private static final AtomicInteger IP = new AtomicInteger(1);

    @Autowired protected WebApplicationContext context;
    @Autowired protected ObjectMapper objectMapper;
    @Autowired protected UserRepository userRepository;
    @Autowired protected PasswordEncoder passwordEncoder;
    @Autowired protected MessagingTestConfig.Outbox outbox;

    protected MockMvc mvc;

    @BeforeEach
    void buildMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        outbox.clear();
    }

    // ---- identifiers ------------------------------------------------------------------------------------------

    protected static String uniquePhone() {
        return "+9477" + String.format("%07d", SEQ.incrementAndGet());
    }

    protected static String uniqueEmail(String prefix) {
        return prefix + SEQ.incrementAndGet() + "@example.com";
    }

    /** A different client address per request, so the per-IP rate limiter never interferes with a test. */
    protected static RequestPostProcessor freshIp() {
        return request -> {
            request.setRemoteAddr("10.20." + (IP.get() / 250 % 250) + "." + (IP.incrementAndGet() % 250 + 1));
            return request;
        };
    }

    // ---- HTTP helpers -----------------------------------------------------------------------------------------

    protected ResultActions send(MockHttpServletRequestBuilder builder, Object body, String bearer, Cookie cookie) throws Exception {
        builder.with(freshIp());
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(body instanceof String s ? s : objectMapper.writeValueAsString(body));
        }
        if (bearer != null) {
            builder.header("Authorization", "Bearer " + bearer);
        }
        if (cookie != null) {
            builder.cookie(cookie);
        }
        return mvc.perform(builder);
    }

    protected ResultActions postJson(String path, Object body) throws Exception {
        return send(post(path), body, null, null);
    }

    protected ResultActions postJson(String path, Object body, String bearer) throws Exception {
        return send(post(path), body, bearer, null);
    }

    protected ResultActions putJson(String path, Object body, String bearer) throws Exception {
        return send(put(path), body, bearer, null);
    }

    protected ResultActions patchJson(String path, Object body, String bearer) throws Exception {
        return send(patch(path), body, bearer, null);
    }

    protected ResultActions getJson(String path, String bearer) throws Exception {
        return send(get(path), null, bearer, null);
    }

    protected ResultActions deleteJson(String path, Object body, String bearer) throws Exception {
        return send(delete(path), body, bearer, null);
    }

    protected static String jsonOf(MvcResult result, String path) throws Exception {
        Object v = JsonPath.read(result.getResponse().getContentAsString(), path);
        return v == null ? null : v.toString();
    }

    // ---- flows ------------------------------------------------------------------------------------------------

    /** send-otp + verify-otp through the API, reading the code from the recorded SMS. */
    protected void verifyPhone(String phone) throws Exception {
        postJson("/api/auth/send-otp", java.util.Map.of("phoneNumber", phone)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        String code = lastOtpCode(phone);
        postJson("/api/auth/verify-otp", java.util.Map.of("phoneNumber", phone, "otpCode", code))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
    }

    protected String lastOtpCode(String phone) {
        MessagingTestConfig.Sms sms = outbox.lastSmsTo(com.suwasarana.api.user.PhoneNumbers.normalize(phone));
        if (sms == null) {
            throw new AssertionError("no SMS was sent to " + phone);
        }
        Matcher m = Pattern.compile("(?<!\\d)(\\d{6})(?!\\d)").matcher(sms.text());
        if (!m.find()) {
            throw new AssertionError("no code in SMS: " + sms.text());
        }
        return m.group(1);
    }

    /** Pulls the token out of the last email to an address (".../verify-email?token=XYZ"). */
    protected String tokenFromLastEmail(String email) {
        var mail = outbox.lastEmailTo(email);
        if (mail == null) {
            throw new AssertionError("no email was sent to " + email);
        }
        Matcher m = Pattern.compile("token=([A-Za-z0-9_%\\-]+)").matcher(mail.body());
        if (!m.find()) {
            throw new AssertionError("no token link in email: " + mail.body());
        }
        return m.group(1);
    }

    public static class Session {
        public Long userId;
        public String email;
        public String phone;
        public String password;
        public String accessToken;
        public Cookie refreshCookie;
    }

    protected Session registerUser(Role role) throws Exception {
        return registerUser(role, uniqueEmail("user"), uniquePhone(), STRONG_PASSWORD);
    }

    protected Session registerUser(Role role, String email, String phone, String password) throws Exception {
        verifyPhone(phone);
        MvcResult result = postJson("/api/auth/register", java.util.Map.of(
                "email", email, "phoneNumber", phone, "password", password, "firstName", "Test", "lastName", "User",
                "role", role.name(), "acceptTerms", true))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andReturn();
        return sessionFrom(result, email, phone, password);
    }

    protected Session sessionFrom(MvcResult result, String email, String phone, String password) throws Exception {
        Session s = new Session();
        s.email = email;
        s.phone = phone;
        s.password = password;
        s.userId = Long.valueOf(jsonOf(result, "$.data.userId"));
        s.accessToken = jsonOf(result, "$.data.accessToken");
        s.refreshCookie = result.getResponse().getCookie("refresh_token");
        return s;
    }

    protected Session login(String identifier, String password) throws Exception {
        MvcResult result = postJson("/api/auth/login", java.util.Map.of("username", identifier, "password", password))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andReturn();
        return sessionFrom(result, identifier, null, password);
    }

    /** Creates a verified administrator directly in the database (invitations are tested separately) and signs in. */
    protected Session createAdmin() throws Exception {
        User admin = new User();
        admin.setEmail(uniqueEmail("admin"));
        admin.setPhoneNumber(uniquePhone());
        admin.setFirstName("Ada");
        admin.setLastName("Admin");
        admin.setRole(Role.ADMIN);
        admin.setPasswordHash(passwordEncoder.encode(STRONG_PASSWORD));
        admin.setVerificationStatus(VerificationStatus.VERIFIED);
        admin.setEmailVerifiedAt(Instant.now());
        userRepository.save(admin);
        Session s = login(admin.getEmail(), STRONG_PASSWORD);
        s.phone = admin.getPhoneNumber();
        return s;
    }

    protected static String bearer(Session s) {
        return s.accessToken;
    }
}
