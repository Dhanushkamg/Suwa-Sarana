package com.suwasarana.api.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimitServiceTest {

    private static RateLimitProperties bind(Map<String, String> map) {
        return new Binder(new MapConfigurationPropertySource(map))
                .bind("app.rate-limit", RateLimitProperties.class)
                .orElseGet(RateLimitProperties::new);
    }

    private static RateLimitService service(Map<String, String> map) {
        RateLimitProperties props = bind(map);
        return new RateLimitService(props, new BucketStore(props.getMaxBuckets()));
    }

    @Test
    @DisplayName("Properties bind in kebab case with durations; unset values keep the built-in defaults")
    void propertiesBind() {
        RateLimitProperties p = bind(Map.of(
                "app.rate-limit.trusted-proxies", "10.0.0.0/8,172.16.0.0/12",
                "app.rate-limit.groups.send-otp.id-limit", "7",
                "app.rate-limit.groups.send-otp.id-window", "30m"));
        assertEquals(2, p.getTrustedProxies().size());
        RateLimitProperties.Effective sendOtp = p.effective(RateGroup.SEND_OTP);
        assertEquals(7, sendOtp.idLimit());
        assertEquals(Duration.ofMinutes(30), sendOtp.idWindow());
        assertEquals(10, sendOtp.ipLimit(), "ip limit of the group is untouched");
        assertEquals(3, new RateLimitProperties().effective(RateGroup.SEND_OTP).idLimit());
        assertEquals(Duration.ofHours(1), new RateLimitProperties().effective(RateGroup.SEND_OTP).idWindow());
    }

    @Test
    @DisplayName("Group keys may be written send-otp, sendOtp or SEND_OTP")
    void groupKeyIsLenient() {
        assertEquals(4, bind(Map.of("app.rate-limit.groups.sendOtp.id-limit", "4")).effective(RateGroup.SEND_OTP).idLimit());
    }

    @Test
    @DisplayName("The multiplier scales every limit but never rounds an enabled limit down to zero")
    void multiplier() {
        RateLimitProperties p = bind(Map.of("app.rate-limit.multiplier", "0.01"));
        assertEquals(1, p.effective(RateGroup.LOGIN).ipLimit());
        assertEquals(0, p.effective(RateGroup.LOGIN).userLimit(), "disabled stays disabled");
        assertEquals(2400, bind(Map.of("app.rate-limit.multiplier", "20")).effective(RateGroup.IVR).ipLimit());
    }

    @Test
    @DisplayName("Per-user daily cap blocks after N requests even when the short window would allow more")
    void userDailyCap() {
        RateLimitService s = service(Map.of(
                "app.rate-limit.groups.ai-draft.user-limit", "1000",
                "app.rate-limit.groups.ai-draft.user-daily-limit", "3"));
        for (int i = 0; i < 3; i++) {
            assertTrue(s.checkUser(RateGroup.AI_DRAFT, "alice@test.com").allowed());
        }
        RateLimitService.Decision d = s.checkUser(RateGroup.AI_DRAFT, "alice@test.com");
        assertFalse(d.allowed());
        assertEquals(RateLimitService.Scope.USER_DAILY, d.scope());
        assertTrue(d.retryAfterSeconds() > 3600, "a daily cap must advertise a long Retry-After");
        assertTrue(s.checkUser(RateGroup.AI_DRAFT, "bob@test.com").allowed(), "other users are unaffected");
    }

    @Test
    @DisplayName("Per-IP and per-user limits are independent of each other")
    void scopesAreIndependent() {
        RateLimitService s = service(Map.of("app.rate-limit.groups.request-create.ip-limit", "2"));
        assertTrue(s.checkIp(RateGroup.REQUEST_CREATE, "1.1.1.1").allowed());
        assertTrue(s.checkIp(RateGroup.REQUEST_CREATE, "1.1.1.1").allowed());
        assertFalse(s.checkIp(RateGroup.REQUEST_CREATE, "1.1.1.1").allowed());
        assertTrue(s.checkUser(RateGroup.REQUEST_CREATE, "alice@test.com").allowed());
        assertTrue(s.checkIp(RateGroup.REQUEST_CREATE, "2.2.2.2").allowed());
    }

    @Test
    @DisplayName("Phone numbers in any notation (local, +94, spaces) and e-mails in any case share one identifier bucket")
    void identifierNormalisation() {
        assertEquals(Identifiers.normalise("0771234567"), Identifiers.normalise("+94 77 123 4567"));
        assertEquals(Identifiers.normalise("0771234567"), Identifiers.normalise("94771234567"));
        assertEquals(Identifiers.normalise("0771234567"), Identifiers.normalise("0094-77-1234567"));
        assertEquals(Identifiers.normalise("Alice@Test.com "), Identifiers.normalise("alice@test.com"));
        assertFalse(Identifiers.normalise("0771234567").equals(Identifiers.normalise("0771234568")));
        assertNull(Identifiers.normalise("   "));
    }

    @Test
    @DisplayName("Send-OTP allows 3 per target phone per hour, whatever the notation")
    void otpPerPhone() {
        RateLimitService s = service(Map.of());
        for (String notation : new String[] {"0771234567", "+94771234567", "94 77 123 4567"}) {
            assertTrue(s.checkIdentifier(RateGroup.SEND_OTP, Identifiers.normalise(notation)).allowed());
        }
        RateLimitService.Decision d = s.checkIdentifier(RateGroup.SEND_OTP, Identifiers.normalise("077-123-4567"));
        assertFalse(d.allowed());
        assertEquals(RateLimitService.Scope.IDENTIFIER, d.scope());
        assertTrue(s.checkIdentifier(RateGroup.SEND_OTP, Identifiers.normalise("0779999999")).allowed());
    }

    @Test
    @DisplayName("Groups are matched by method and path")
    void groupMatching() {
        assertEquals(RateGroup.LOGIN, RateGroup.match("POST", "/api/auth/login"));
        assertEquals(RateGroup.GOOGLE, RateGroup.match("POST", "/api/auth/google/verify"));
        assertEquals(RateGroup.AUTH_OTHER, RateGroup.match("POST", "/api/auth/forgot-password"));
        assertNull(RateGroup.match("POST", "/api/auth/refresh"));
        assertNull(RateGroup.match("POST", "/api/auth/logout"));
        assertNull(RateGroup.match("GET", "/api/auth/me"));
        assertEquals(RateGroup.FAQ, RateGroup.match("POST", "/api/faq/ask"));
        assertEquals(RateGroup.AI_DRAFT, RateGroup.match("POST", "/api/requests/ai-draft"));
        assertEquals(RateGroup.REQUEST_CREATE, RateGroup.match("POST", "/api/requests"));
        assertNull(RateGroup.match("GET", "/api/requests"));
        assertEquals(RateGroup.REQUEST_REPORT, RateGroup.match("POST", "/api/requests/42/report"));
        assertNull(RateGroup.match("POST", "/api/requests/42/cancel"));
        assertEquals(RateGroup.CONTACT, RateGroup.match("POST", "/api/contact"));
        assertNull(RateGroup.match("GET", "/api/contact"));
        assertEquals(RateGroup.CIRCLE_RESPOND, RateGroup.match("POST", "/api/circle/abc123/respond"));
        assertNull(RateGroup.match("GET", "/api/circle/abc123"));
        assertEquals(RateGroup.IVR, RateGroup.match("GET", "/api/ivr/webhook"));
        assertEquals(RateGroup.IVR, RateGroup.match("POST", "/api/ivr/webhook"));
        assertNull(RateGroup.match("OPTIONS", "/api/auth/login"));
        assertEquals("abc123", RateGroup.circleTokenOf("/api/circle/abc123/respond"));
    }
}
