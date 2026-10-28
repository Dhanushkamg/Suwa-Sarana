package com.suwasarana.api.ratelimit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.suwasarana.api.security.UserDetailsImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntFunction;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Exercises the rate limits through the real filter chain, not by calling the filter directly:
 * the first class of tests talks to an embedded Tomcat on a random port (so a duplicate servlet-container
 * registration of the filter would show up as limits that trigger too early), the second class uses MockMvc with the
 * Spring Security chain to control the peer address and the authenticated user.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.rate-limit.trusted-proxies=10.0.0.0/8")
@ActiveProfiles("test")
class RateLimitFilterChainTest {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    /** A rate-limited call; the body is deliberately invalid so the controller answers 4xx without side effects. */
    private record Spec(String name, String method, IntFunction<String> path, IntFunction<String> body, int allowed) {
        @Override public String toString() { return name; }
    }

    private static Stream<Spec> ipSpecs() {
        return Stream.of(
                new Spec("login", "POST", i -> "/api/auth/login", i -> "{\"username\":\"user" + i + "@x.com\"}", 20),
                new Spec("register", "POST", i -> "/api/auth/register", i -> "{\"email\":\"user" + i + "@x.com\"}", 10),
                new Spec("send-otp", "POST", i -> "/api/auth/send-otp", i -> "{\"phoneNumber\":\"07712345" + (10 + i) + "ab\"}", 10),
                new Spec("verify-otp", "POST", i -> "/api/auth/verify-otp", i -> "{\"phoneNumber\":\"07712345" + (10 + i) + "\"}", 30),
                new Spec("reset-password", "POST", i -> "/api/auth/reset-password", i -> "{\"phoneNumber\":\"07712345" + (10 + i) + "\"}", 10),
                new Spec("google-verify", "POST", i -> "/api/auth/google/verify", i -> "{}", 20),
                new Spec("google-register", "POST", i -> "/api/auth/google/register", i -> "{}", 20),
                new Spec("auth-other", "POST", i -> "/api/auth/forgot-password", i -> "{\"email\":\"user" + i + "@x.com\"}", 10),
                new Spec("faq", "POST", i -> "/api/faq/ask", i -> "{}", 10),
                new Spec("ai-draft", "POST", i -> "/api/requests/ai-draft", i -> "{}", 20),
                new Spec("contact", "POST", i -> "/api/contact", i -> "{}", 5),
                new Spec("circle-respond", "POST", i -> "/api/circle/token" + i + "/respond", i -> "{}", 10),
                new Spec("request-create", "POST", i -> "/api/requests", i -> "{}", 20),
                new Spec("request-report", "POST", i -> "/api/requests/5/report", i -> "{}", 20),
                new Spec("ivr", "GET", i -> "/api/ivr/webhook", i -> null, 120));
    }

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private RateLimitService limits;

    @Autowired
    private ObjectMapper mapper;

    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void resetBuckets() {
        limits.store().clear();
    }

    private HttpResponse<String> call(Spec spec, int i, String... extraHeaders) throws Exception {
        return call(spec.method(), spec.path().apply(i), spec.body().apply(i), extraHeaders);
    }

    private HttpResponse<String> call(String method, String path, String body, String... extraHeaders) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
        if (body == null) {
            b.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            b.header("Content-Type", JSON).method(method, HttpRequest.BodyPublishers.ofString(body));
        }
        for (int i = 0; i + 1 < extraHeaders.length; i += 2) {
            b.header(extraHeaders[i], extraHeaders[i + 1]);
        }
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    // ---------------------------------------------------------------- registration

    @Test
    @DisplayName("The filter is registered once: its servlet-container registration is disabled")
    void containerRegistrationIsDisabled() {
        @SuppressWarnings("rawtypes")
        Map<String, FilterRegistrationBean> registrations = context.getBeansOfType(FilterRegistrationBean.class);
        boolean found = false;
        for (FilterRegistrationBean<?> r : registrations.values()) {
            if (r.getFilter() instanceof com.suwasarana.api.config.RateLimitingFilter) {
                found = true;
                assertFalse(r.isEnabled(), "RateLimitingFilter must not be registered a second time with the container");
            }
        }
        assertTrue(found, "expected an explicit (disabled) FilterRegistrationBean for RateLimitingFilter");
    }

    // ---------------------------------------------------------------- per IP, every group, real server

    @ParameterizedTest(name = "{0}: exactly the configured number of requests per IP pass, the next one is 429")
    @MethodSource("ipSpecs")
    void perIpLimit_realServer(Spec spec) throws Exception {
        for (int i = 1; i <= spec.allowed(); i++) {
            HttpResponse<String> r = call(spec, i);
            assertNotEquals(429, r.statusCode(), spec.name() + ": request " + i + " of " + spec.allowed() + " was limited too early"
                    + " (double registration of the filter would do this)");
        }
        HttpResponse<String> blocked = call(spec, spec.allowed() + 1);
        assertEquals(429, blocked.statusCode(), spec.name() + ": request " + (spec.allowed() + 1) + " must be limited");

        assertTrue(blocked.headers().firstValue("Retry-After").isPresent(), "Retry-After header");
        assertTrue(Long.parseLong(blocked.headers().firstValue("Retry-After").get()) >= 1);
        assertTrue(blocked.headers().firstValue("Content-Type").orElse("").startsWith("application/json"));
        JsonNode body = mapper.readTree(blocked.body());
        assertFalse(body.get("success").asBoolean());
        assertEquals("rate.limited", body.get("code").asText());
        assertTrue(body.has("data") && body.get("data").isNull());
        assertFalse(body.get("message").asText().isBlank());
        assertTrue(body.hasNonNull("timestamp"));
    }

    @Test
    @DisplayName("Spoofing X-Forwarded-For from an untrusted peer does not give a fresh bucket")
    void spoofedForwardedFor_realServer() throws Exception {
        for (int i = 1; i <= 20; i++) {
            HttpResponse<String> r = call("POST", "/api/auth/login", "{\"username\":\"spoof" + i + "@x.com\"}",
                    "X-Forwarded-For", "198.51.100." + i);
            assertNotEquals(429, r.statusCode(), "request " + i);
        }
        HttpResponse<String> blocked = call("POST", "/api/auth/login", "{\"username\":\"spoof99@x.com\"}",
                "X-Forwarded-For", "203.0.113.77");
        assertEquals(429, blocked.statusCode());
    }

    @Test
    @DisplayName("Rate limiting does not swallow the request body: the controller still sees it")
    void bodyReachesController() throws Exception {
        // valid shape, unknown account: reaches AuthService (401), proving the body was replayed after being peeked
        HttpResponse<String> r = call("POST", "/api/auth/login", "{\"username\":\"nobody@x.com\",\"password\":\"password123\"}");
        assertEquals(401, r.statusCode(), r.body());
    }

    // ---------------------------------------------------------------- per identifier, real server

    @Test
    @DisplayName("Login: 10 attempts per e-mail address, from any number of IPs")
    void loginPerIdentifier() throws Exception {
        for (int i = 1; i <= 10; i++) {
            assertNotEquals(429, call("POST", "/api/auth/login", "{\"username\":\"Victim@Example.com\"}").statusCode(), "attempt " + i);
        }
        assertEquals(429, call("POST", "/api/auth/login", "{\"username\":\"victim@example.com\"}").statusCode());
        // a different account from the same IP is still fine
        assertNotEquals(429, call("POST", "/api/auth/login", "{\"username\":\"other@example.com\"}").statusCode());
    }

    @Test
    @DisplayName("Send-OTP: 3 per target phone per hour, whichever notation is used")
    void sendOtpPerPhone() throws Exception {
        String[] notations = {"0771234567ab", "+94771234567ab", "94 77 123 4567 ab"};
        for (String n : notations) {
            assertNotEquals(429, call("POST", "/api/auth/send-otp", "{\"phoneNumber\":\"" + n + "\"}").statusCode(), n);
        }
        assertEquals(429, call("POST", "/api/auth/send-otp", "{\"phoneNumber\":\"077-123-4567ab\"}").statusCode());
        assertNotEquals(429, call("POST", "/api/auth/send-otp", "{\"phoneNumber\":\"0779998887ab\"}").statusCode());
    }

    @Test
    @DisplayName("Register, verify-otp and reset-password are limited per identifier too")
    void otherIdentifierGroups() throws Exception {
        for (int i = 1; i <= 5; i++) {
            assertNotEquals(429, call("POST", "/api/auth/register", "{\"email\":\"same@x.com\"}").statusCode());
            assertNotEquals(429, call("POST", "/api/auth/reset-password", "{\"phoneNumber\":\"0771111111\"}").statusCode());
        }
        assertEquals(429, call("POST", "/api/auth/register", "{\"email\":\"same@x.com\"}").statusCode());
        assertEquals(429, call("POST", "/api/auth/reset-password", "{\"phoneNumber\":\"0771111111\"}").statusCode());

        for (int i = 1; i <= 10; i++) {
            assertNotEquals(429, call("POST", "/api/auth/verify-otp", "{\"phoneNumber\":\"0772222222\"}").statusCode());
        }
        assertEquals(429, call("POST", "/api/auth/verify-otp", "{\"phoneNumber\":\"0772222222\"}").statusCode());
    }

    // ---------------------------------------------------------------- MockMvc: peer address, proxies, users

    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private static RequestPostProcessor peer(String remoteAddr) {
        return r -> { r.setRemoteAddr(remoteAddr); return r; };
    }

    private int loginFrom(MockMvc mvc, String remoteAddr, String xff, int n) throws Exception {
        var req = post("/api/auth/login").contentType(JSON).content("{\"username\":\"n" + n + "@x.com\"}").with(peer(remoteAddr));
        if (xff != null) {
            req.header("X-Forwarded-For", xff);
        }
        return mvc.perform(req).andReturn().getResponse().getStatus();
    }

    @Test
    @DisplayName("Different client IPs have independent buckets")
    void ipIsolation() throws Exception {
        MockMvc mvc = mvc();
        for (int i = 1; i <= 20; i++) {
            assertNotEquals(429, loginFrom(mvc, "192.0.2.10", null, i));
        }
        assertEquals(429, loginFrom(mvc, "192.0.2.10", null, 21));
        assertNotEquals(429, loginFrom(mvc, "192.0.2.11", null, 22));
    }

    @Test
    @DisplayName("Behind a trusted proxy each real client gets its own bucket; the proxy address is not the key")
    void trustedProxy_clientsAreSeparated() throws Exception {
        MockMvc mvc = mvc();
        for (int i = 1; i <= 20; i++) {
            assertNotEquals(429, loginFrom(mvc, "10.0.0.5", "203.0.113.1", i));
        }
        assertEquals(429, loginFrom(mvc, "10.0.0.5", "203.0.113.1", 21));
        // another visitor through the same load balancer is not affected
        assertNotEquals(429, loginFrom(mvc, "10.0.0.5", "203.0.113.2", 22));
        // and a client cannot dodge its bucket by prepending a made-up address to the header
        assertEquals(429, loginFrom(mvc, "10.0.0.5", "6.6.6.6, 203.0.113.1", 23));
    }

    @Test
    @DisplayName("From an untrusted peer the X-Forwarded-For header is ignored")
    void untrustedPeer_headerIgnored() throws Exception {
        MockMvc mvc = mvc();
        for (int i = 1; i <= 20; i++) {
            assertNotEquals(429, loginFrom(mvc, "192.0.2.50", "9.9.9." + i, i));
        }
        assertEquals(429, loginFrom(mvc, "192.0.2.50", "8.8.8.8", 21));
    }

    @Test
    @DisplayName("Circle pledges: 30 per invite token per hour even when every request comes from a different IP")
    void circleRespond_perToken() throws Exception {
        MockMvc mvc = mvc();
        String token = UUID.randomUUID().toString().replace("-", "");
        for (int i = 1; i <= 30; i++) {
            int status = mvc.perform(post("/api/circle/" + token + "/respond").contentType(JSON).content("{}")
                    .with(peer("192.0.3." + i))).andReturn().getResponse().getStatus();
            assertNotEquals(429, status, "pledge " + i);
        }
        int blocked = mvc.perform(post("/api/circle/" + token + "/respond").contentType(JSON).content("{}")
                .with(peer("192.0.3.200"))).andReturn().getResponse().getStatus();
        assertEquals(429, blocked);
        // a different circle is unaffected
        int other = mvc.perform(post("/api/circle/" + UUID.randomUUID().toString().replace("-", "") + "/respond")
                .contentType(JSON).content("{}").with(peer("192.0.3.201"))).andReturn().getResponse().getStatus();
        assertNotEquals(429, other);
    }

    private static RequestPostProcessor asUser(String email) {
        return user(new UserDetailsImpl(Math.abs(email.hashCode()) + 1L, email, "x", "REQUESTER"));
    }

    private int postAs(MockMvc mvc, String path, String email) throws Exception {
        var req = post(path).contentType(JSON).content("{}").with(peer("192.0.2.77"));
        if (email != null) {
            req.with(asUser(email));
        }
        MvcResult r = mvc.perform(req).andReturn();
        return r.getResponse().getStatus();
    }

    @Test
    @DisplayName("AI draft: 5 per user per minute, other users and the per-IP budget are separate")
    void aiDraft_perUser() throws Exception {
        MockMvc mvc = mvc();
        for (int i = 1; i <= 5; i++) {
            assertNotEquals(429, postAs(mvc, "/api/requests/ai-draft", "alice@test.com"), "call " + i);
        }
        MvcResult blocked = mvc.perform(post("/api/requests/ai-draft").contentType(JSON).content("{}")
                .with(peer("192.0.2.77")).with(asUser("alice@test.com"))).andReturn();
        assertEquals(429, blocked.getResponse().getStatus());
        assertTrue(Long.parseLong(blocked.getResponse().getHeader("Retry-After")) >= 1);
        assertEquals("rate.limited", mapper.readTree(blocked.getResponse().getContentAsString()).get("code").asText());
        assertNotEquals(429, postAs(mvc, "/api/requests/ai-draft", "bob@test.com"));
    }

    @Test
    @DisplayName("Creating requests: 5 per user per 10 minutes")
    void requestCreate_perUser() throws Exception {
        MockMvc mvc = mvc();
        for (int i = 1; i <= 5; i++) {
            assertNotEquals(429, postAs(mvc, "/api/requests", "carol@test.com"), "call " + i);
        }
        assertEquals(429, postAs(mvc, "/api/requests", "carol@test.com"));
        assertNotEquals(429, postAs(mvc, "/api/requests", "dave@test.com"));
    }

    @Test
    @DisplayName("Reporting a request: 5 per user per 10 minutes")
    void requestReport_perUser() throws Exception {
        MockMvc mvc = mvc();
        for (int i = 1; i <= 5; i++) {
            assertNotEquals(429, postAs(mvc, "/api/requests/5/report", "erin@test.com"), "call " + i);
        }
        assertEquals(429, postAs(mvc, "/api/requests/5/report", "erin@test.com"));
        assertNotEquals(429, postAs(mvc, "/api/requests/5/report", "frank@test.com"));
    }

    @Test
    @DisplayName("Per-user buckets do not apply to anonymous callers (only the IP limit does)")
    void anonymousIsNotCountedPerUser() throws Exception {
        MockMvc mvc = mvc();
        for (int i = 1; i <= 8; i++) {
            assertEquals(401, postAs(mvc, "/api/requests", null), "anonymous call " + i);
        }
    }

}
