package com.suwasarana.api.security;

import com.suwasarana.api.auth.AuthService;
import com.suwasarana.api.config.RateLimitingFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    @Mock
    private FilterChain filterChain;

    @Test
    @DisplayName("Refresh token hashing uses SHA-256, not MD5")
    void refreshTokenHash_usesSha256NotMd5() {
        String input = "test-refresh-token-value";
        String sha256Hash = AuthService.sha256Hex(input);
        assertEquals(64, sha256Hash.length());
        String md5Hash = org.springframework.util.DigestUtils.md5DigestAsHex(input.getBytes());
        assertNotEquals(md5Hash, sha256Hash);
        assertEquals(sha256Hash, AuthService.sha256Hex(input));
    }

    @Test
    @DisplayName("SHA-256 hash does not contain the raw token")
    void sha256Hash_doesNotContainRawToken() {
        String rawToken = "super-secret-refresh-token";
        String hash = AuthService.sha256Hex(rawToken);
        assertFalse(hash.contains(rawToken));
    }

    @Test
    @DisplayName("Rate limiter blocks on RemoteAddr even when X-Forwarded-For changes")
    void rateLimiter_usesRemoteAddr_notXForwardedFor() throws ServletException, IOException {
        RateLimitingFilter filter = new RateLimitingFilter();
        for (int i = 0; i <= 10; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/login");
            req.setRemoteAddr("10.0.0.1");
            req.addHeader("X-Forwarded-For", "1.2.3." + i);
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(req, res, filterChain);
            if (i == 10) {
                assertEquals(429, res.getStatus());
            }
        }
    }
}
