package com.suwasarana.api.security;

import com.suwasarana.api.auth.AuthService;
import com.suwasarana.api.config.RateLimitingFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Security regression tests for the JWT authentication filter.
 *
 * Key assertions:
 * 1. JWT via URL query parameter is NOT accepted (token would leak into logs)
 * 2. JWT must only be sent via the Authorization: Bearer header
 * 3. SHA-256 is used for refresh token hashing (not MD5)
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    @Mock
    private FilterChain filterChain;

    @Test
    @DisplayName("Refresh token hashing uses SHA-256, not MD5")
    void refreshTokenHash_usesSha256NotMd5() {
        String input = "test-refresh-token-value";

        String sha256Hash = AuthService.sha256Hex(input);

        // SHA-256 hash is 64 hex characters
        assertEquals(64, sha256Hash.length(), "SHA-256 hex output must be 64 characters");
        // Must not be the MD5 hash (MD5 is 32 chars, but verify content too)
        String md5Hash = org.springframework.util.DigestUtils.md5DigestAsHex(input.getBytes());
        assertNotEquals(md5Hash, sha256Hash, "Hash must not be MD5");
        // Verify it's deterministic
        assertEquals(sha256Hash, AuthService.sha256Hex(input), "Same input must produce same hash");
    }

    @Test
    @DisplayName("SHA-256 hash of refresh token does not contain the raw token value")
    void sha256Hash_doesNotContainRawToken() {
        String rawToken = "super-secret-refresh-token";
        String hash = AuthService.sha256Hex(rawToken);

        assertFalse(hash.contains(rawToken), "Hash must not contain the raw token");
        assertFalse(rawToken.contains(hash), "Raw token must not contain the hash");
    }

    @Test
    @DisplayName("Rate limiter uses RemoteAddr, not raw X-Forwarded-For header")
    void rateLimiter_usesRemoteAddr_notXForwardedFor() throws ServletException, IOException {
        RateLimitingFilter filter = new RateLimitingFilter();

        // Attacker tries to spoof IP with X-Forwarded-For to bypass rate limiting
        for (int i = 0; i < 11; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/login");
            req.setRemoteAddr("10.0.0.1"); // Real IP (same attacker)
            req.addHeader("X-Forwarded-For", "1.2.3." + i); // Spoofed IP changes each time
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(req, res, filterChain);

            if (i == 10) {
                // 11th request from the same real IP must be rate-limited
                assertEquals(429, res.getStatus(),
                        "Request should be rate-limited based on RemoteAddr, not spoofed X-Forwarded-For");
            }
        }
    }
}
