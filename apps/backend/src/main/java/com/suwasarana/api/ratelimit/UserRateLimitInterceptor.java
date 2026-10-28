package com.suwasarana.api.ratelimit;

import com.suwasarana.api.config.RateLimitingFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Per-user (and per-user daily) limits for the cost / spam sensitive endpoints. It runs inside the dispatcher, i.e.
 * after the Spring Security chain, so the authenticated principal is known. Requests without an authenticated
 * principal are not counted here (they were already limited per IP by {@link RateLimitingFilter}).
 */
public class UserRateLimitInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(UserRateLimitInterceptor.class);

    private final RateLimitService limits;

    public UserRateLimitInterceptor(RateLimitService limits) {
        this.limits = limits;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!limits.isEnabled()) {
            return true;
        }
        RateGroup group = RateGroup.match(request.getMethod(), RateLimitingFilter.pathOf(request));
        if (group == null || !limits.rule(group).hasUserScope()) {
            return true;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return true;
        }
        RateLimitService.Decision decision = limits.checkUser(group, auth.getName());
        if (decision.allowed()) {
            return true;
        }
        log.warn("Rate limit exceeded: group={} scope={} retryAfter={}s", group.key(), decision.scope(), decision.retryAfterSeconds());
        RateLimitResponses.tooManyRequests(response, decision.retryAfterSeconds());
        return false;
    }
}
