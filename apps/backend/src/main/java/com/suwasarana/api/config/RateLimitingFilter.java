package com.suwasarana.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.suwasarana.api.ratelimit.CachedBodyRequestFactory;
import com.suwasarana.api.ratelimit.ClientIpResolver;
import com.suwasarana.api.ratelimit.RateGroup;
import com.suwasarana.api.ratelimit.RateLimitProperties;
import com.suwasarana.api.ratelimit.RateLimitService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Pre-authentication abuse protection: per-client-IP and per-identifier (hashed e-mail / phone / circle token)
 * token buckets for the endpoint groups in {@link RateGroup}. Per-user limits need an authenticated principal and are
 * applied afterwards by {@code UserRateLimitInterceptor}.
 *
 * <p>This is a {@link OncePerRequestFilter} that is added <b>once</b>, to the Spring Security chain
 * ({@code SecurityConfig}); its servlet-container registration is disabled in
 * {@code RateLimitConfig}. Even if it were registered twice, the once-per-request guard would make the second
 * pass a no-op, so a request is never counted twice.
 */
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    /** Request attribute holding the resolved client IP (usable by controllers / audit logging). */
    public static final String CLIENT_IP_ATTRIBUTE = RateLimitingFilter.class.getName() + ".CLIENT_IP";

    private static final Logger log = LoggerFactory.getLogger(RateLimitingFilter.class);

    private final RateLimitService limits;
    private final ClientIpResolver ipResolver;
    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;

    @Autowired
    public RateLimitingFilter(RateLimitService limits, ClientIpResolver ipResolver,
                              RateLimitProperties properties, ObjectMapper objectMapper) {
        this.limits = limits;
        this.ipResolver = ipResolver;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !properties.isEnabled() || RateGroup.match(request.getMethod(), pathOf(request)) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = pathOf(request);
        RateGroup group = RateGroup.match(request.getMethod(), path);
        if (group == null) { // shouldNotFilter already filtered this, kept for direct callers
            chain.doFilter(request, response);
            return;
        }

        String clientIp = ipResolver.resolve(request);
        request.setAttribute(CLIENT_IP_ATTRIBUTE, clientIp);

        RateLimitService.Decision decision = limits.checkIp(group, clientIp);
        if (!decision.allowed()) {
            reject(response, group, decision);
            return;
        }

        HttpServletRequest downstream = request;
        if (limits.rule(group).hasId()) {
            String identifier;
            String circleToken = RateGroup.circleTokenOf(path);
            if (circleToken != null) {
                identifier = "t:" + circleToken;
            } else {
                CachedBodyRequestFactory.Peeked peeked = CachedBodyRequestFactory.peekJson(request, properties.getMaxBodyBytes(), objectMapper);
                downstream = peeked.request();
                identifier = peeked.identifier();
            }
            decision = limits.checkIdentifier(group, identifier);
            if (!decision.allowed()) {
                reject(response, group, decision);
                return;
            }
        }

        chain.doFilter(downstream, response);
    }

    private void reject(HttpServletResponse response, RateGroup group, RateLimitService.Decision decision) throws IOException {
        log.warn("Rate limit exceeded: group={} scope={} retryAfter={}s", group.key(), decision.scope(), decision.retryAfterSeconds());
        ApiErrorWriter.writeTooManyRequests(response, decision.retryAfterSeconds());
    }

    /** Path without context path, matrix parameters, duplicate/trailing slashes. */
    public static String pathOf(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null) {
            return "";
        }
        String ctx = request.getContextPath();
        if (ctx != null && !ctx.isEmpty() && uri.startsWith(ctx)) {
            uri = uri.substring(ctx.length());
        }
        int semi = uri.indexOf(';');
        if (semi >= 0) {
            uri = uri.substring(0, semi);
        }
        while (uri.contains("//")) {
            uri = uri.replace("//", "/");
        }
        if (uri.length() > 1 && uri.endsWith("/")) {
            uri = uri.substring(0, uri.length() - 1);
        }
        return uri;
    }
}
