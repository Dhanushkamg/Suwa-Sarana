package com.suwasarana.api.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitingFilter implements Filter {

    // Cache of Client IP -> Bucket for Auth endpoints (10 req/min)
    private final Map<String, Bucket> authBuckets = new ConcurrentHashMap<>();

    // Cache of Client IP/User -> Bucket for Blood Request creation (5 req/min)
    private final Map<String, Bucket> requestCreationBuckets = new ConcurrentHashMap<>();

    private Bucket createAuthBucket() {
        // 10 requests per minute per IP for authentication
        Bandwidth limit = Bandwidth.classic(10, Refill.greedy(10, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limit).build();
    }

    private Bucket createRequestCreationBucket() {
        // 5 requests per minute per IP for creating blood requests
        Bandwidth limit = Bandwidth.classic(5, Refill.greedy(5, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limit).build();
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        String path = req.getRequestURI();
        String method = req.getMethod();
        String clientIp = getClientIp(req);

        // Rate limit POST /api/auth/login and POST /api/auth/register
        if (("POST".equalsIgnoreCase(method) && (path.equals("/api/auth/login") || path.equals("/api/auth/register")))) {
            Bucket bucket = authBuckets.computeIfAbsent(clientIp, k -> createAuthBucket());
            if (!bucket.tryConsume(1)) {
                sendRateLimitExceededResponse(res);
                return;
            }
        }

        // Rate limit POST /api/requests
        if ("POST".equalsIgnoreCase(method) && path.equals("/api/requests")) {
            Bucket bucket = requestCreationBuckets.computeIfAbsent(clientIp, k -> createRequestCreationBucket());
            if (!bucket.tryConsume(1)) {
                sendRateLimitExceededResponse(res);
                return;
            }
        }

        chain.doFilter(request, response);
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "unknown";
    }

    private void sendRateLimitExceededResponse(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"status\":429,\"message\":\"Too many requests. Please slow down and try again later.\",\"data\":null}");
    }

    // Accessible for test state reset if needed
    public void clearBuckets() {
        authBuckets.clear();
        requestCreationBuckets.clear();
    }
}
