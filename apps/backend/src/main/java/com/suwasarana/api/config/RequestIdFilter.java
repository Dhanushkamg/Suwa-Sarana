package com.suwasarana.api.config;

import com.suwasarana.api.common.RequestContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Correlation id + access log (audit F-32).
 *
 * <p>Runs first (before Spring Security), so even 401/403/429 responses carry an {@code X-Request-Id} header and every
 * log line of the request has the id in the MDC ({@code requestId}). A caller/ALB-supplied id is reused only when it
 * is a short token of safe characters (prevents log injection); otherwise a UUID is generated.</p>
 *
 * <p>The access log line is PII-safe by construction: it records the HTTP method, the <em>route template</em>
 * (e.g. {@code /api/circle/{token}}, never the concrete URL), status and duration - no query string, headers, body,
 * client address or user identifier.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    private static final Logger accessLog = LoggerFactory.getLogger("http.access");
    private static final Pattern SAFE_ID = Pattern.compile("^[A-Za-z0-9._-]{8,64}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String incoming = request.getHeader(RequestContext.REQUEST_ID_HEADER);
        String requestId = incoming != null && SAFE_ID.matcher(incoming).matches() ? incoming : UUID.randomUUID().toString();

        MDC.put(RequestContext.MDC_REQUEST_ID, requestId);
        response.setHeader(RequestContext.REQUEST_ID_HEADER, requestId);
        long start = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            logAccess(request, response, (System.nanoTime() - start) / 1_000_000);
            MDC.remove(RequestContext.MDC_REQUEST_ID);
        }
    }

    private void logAccess(HttpServletRequest request, HttpServletResponse response, long millis) {
        String uri = request.getRequestURI();
        if (uri != null && uri.startsWith("/actuator/health")) {
            return; // probes fire every few seconds; not worth a log line each
        }
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String route = pattern != null ? pattern.toString() : "<unmatched>";
        int status = response.getStatus();
        if (status >= 500) {
            accessLog.warn("{} {} -> {} in {} ms", request.getMethod(), route, status, millis);
        } else {
            accessLog.info("{} {} -> {} in {} ms", request.getMethod(), route, status, millis);
        }
    }
}
