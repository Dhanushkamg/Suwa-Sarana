package com.suwasarana.api.ratelimit;

import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/** Writes the standard {@code ApiResponse}-shaped 429 body. */
public final class RateLimitResponses {

    public static final String CODE = "rate.limited";
    public static final String MESSAGE = "Too many requests. Please slow down and try again later.";

    private RateLimitResponses() {
    }

    public static void tooManyRequests(HttpServletResponse response, long retryAfterSeconds) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(429);
        response.setHeader("Retry-After", Long.toString(Math.max(1, retryAfterSeconds)));
        response.setHeader("Cache-Control", "no-store");
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        String body = "{\"success\":false,\"message\":\"" + MESSAGE + "\",\"code\":\"" + CODE
                + "\",\"data\":null,\"timestamp\":\"" + LocalDateTime.now() + "\"}";
        response.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
        response.flushBuffer();
    }
}
