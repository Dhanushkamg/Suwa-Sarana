package com.suwasarana.api.security;

import com.suwasarana.api.config.RateLimitingFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class RateLimitTest {

    private RateLimitingFilter rateLimitingFilter;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        rateLimitingFilter = new RateLimitingFilter();
        filterChain = mock(FilterChain.class);
    }

    @Test
    @DisplayName("Should allow 10 auth requests per minute and reject 11th with 429")
    void whenAuthLimitExceeded_returns429() throws ServletException, IOException {
        String clientIp = "192.168.1.100";

        // First 10 requests should succeed
        for (int i = 1; i <= 10; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
            request.setRemoteAddr(clientIp);
            MockHttpServletResponse response = new MockHttpServletResponse();

            rateLimitingFilter.doFilter(request, response, filterChain);
            assertEquals(200, response.getStatus(), "Request " + i + " should succeed");
        }

        verify(filterChain, times(10)).doFilter(any(), any());

        // 11th request should be blocked with 429
        MockHttpServletRequest blockedRequest = new MockHttpServletRequest("POST", "/api/auth/login");
        blockedRequest.setRemoteAddr(clientIp);
        MockHttpServletResponse blockedResponse = new MockHttpServletResponse();

        rateLimitingFilter.doFilter(blockedRequest, blockedResponse, filterChain);

        assertEquals(429, blockedResponse.getStatus());
        assertTrue(blockedResponse.getContentType().startsWith(MediaType.APPLICATION_JSON_VALUE));
        assertTrue(blockedResponse.getContentAsString().contains("Too many requests"));
        // Verify chain was NOT called on 11th attempt
        verify(filterChain, times(10)).doFilter(any(), any());
    }

    @Test
    @DisplayName("Rate limits apply independently across different client IPs")
    void rateLimitsAreIsolatedByClientIp() throws ServletException, IOException {
        String ip1 = "10.0.0.1";
        String ip2 = "10.0.0.2";

        // Exhaust IP 1
        for (int i = 0; i < 10; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/login");
            req.setRemoteAddr(ip1);
            rateLimitingFilter.doFilter(req, new MockHttpServletResponse(), filterChain);
        }

        // IP 1 is blocked on 11th
        MockHttpServletRequest req1 = new MockHttpServletRequest("POST", "/api/auth/login");
        req1.setRemoteAddr(ip1);
        MockHttpServletResponse res1 = new MockHttpServletResponse();
        rateLimitingFilter.doFilter(req1, res1, filterChain);
        assertEquals(429, res1.getStatus());

        // IP 2 is still allowed
        MockHttpServletRequest req2 = new MockHttpServletRequest("POST", "/api/auth/login");
        req2.setRemoteAddr(ip2);
        MockHttpServletResponse res2 = new MockHttpServletResponse();
        rateLimitingFilter.doFilter(req2, res2, filterChain);
        assertEquals(200, res2.getStatus());
    }

    @Test
    @DisplayName("Should limit POST /api/requests to 5 requests per minute per IP")
    void whenRequestCreationLimitExceeded_returns429() throws ServletException, IOException {
        String clientIp = "172.16.0.5";

        // 5 requests allowed
        for (int i = 1; i <= 5; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/requests");
            req.setRemoteAddr(clientIp);
            MockHttpServletResponse res = new MockHttpServletResponse();
            rateLimitingFilter.doFilter(req, res, filterChain);
            assertEquals(200, res.getStatus());
        }

        // 6th request blocked
        MockHttpServletRequest blockedReq = new MockHttpServletRequest("POST", "/api/requests");
        blockedReq.setRemoteAddr(clientIp);
        MockHttpServletResponse blockedRes = new MockHttpServletResponse();
        rateLimitingFilter.doFilter(blockedReq, blockedRes, filterChain);

        assertEquals(429, blockedRes.getStatus());
        assertTrue(blockedRes.getContentAsString().contains("Too many requests"));
    }

    @Test
    @DisplayName("Non-rate-limited GET endpoints pass through unthrottled")
    void nonRateLimitedEndpointsPassUnthrottled() throws ServletException, IOException {
        String clientIp = "192.168.1.200";

        for (int i = 0; i < 20; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/requests");
            req.setRemoteAddr(clientIp);
            MockHttpServletResponse res = new MockHttpServletResponse();
            rateLimitingFilter.doFilter(req, res, filterChain);
            assertEquals(200, res.getStatus());
        }

        verify(filterChain, times(20)).doFilter(any(), any());
    }
}
