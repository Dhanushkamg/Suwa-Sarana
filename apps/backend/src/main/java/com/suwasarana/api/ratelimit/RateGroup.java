package com.suwasarana.api.ratelimit;

import java.util.regex.Pattern;

/**
 * The endpoint groups that are rate limited. Each group has its own configurable limits
 * ({@code app.rate-limit.groups.<key>.*}, see {@link RateLimitProperties}).
 */
public enum RateGroup {
    LOGIN("login"),
    REGISTER("register"),
    SEND_OTP("send-otp"),
    VERIFY_OTP("verify-otp"),
    RESET_PASSWORD("reset-password"),
    GOOGLE("google"),
    /** Any other state-changing /api/auth/ endpoint (forgot-password, email reset, email verification ...). */
    AUTH_OTHER("auth-other"),
    FAQ("faq"),
    AI_DRAFT("ai-draft"),
    /** POST /api/ai/poster-copy: spends LLM tokens, so it is limited per IP, per user and per user per day like ai-draft. */
    POSTER_COPY("poster-copy"),
    CONTACT("contact"),
    CIRCLE_RESPOND("circle-respond"),
    REQUEST_CREATE("request-create"),
    REQUEST_REPORT("request-report"),
    IVR("ivr");

    private static final Pattern CIRCLE_RESPOND_PATH = Pattern.compile("^/api/circle/([^/]+)/respond$");
    private static final Pattern REQUEST_REPORT_PATH = Pattern.compile("^/api/requests/[^/]+/report$");

    private final String key;

    RateGroup(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    /** Normalises a configuration key so that {@code send-otp}, {@code sendOtp} and {@code SEND_OTP} are the same. */
    public static String normaliseKey(String raw) {
        return raw == null ? "" : raw.replaceAll("[^A-Za-z0-9]", "").toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * Resolves the group for a request, or {@code null} when the request is not rate limited.
     *
     * @param method HTTP method
     * @param path   request path without the context path, query string or trailing slash
     */
    public static RateGroup match(String method, String path) {
        if (method == null || path == null || "OPTIONS".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method)) {
            return null;
        }
        boolean post = "POST".equalsIgnoreCase(method);
        boolean get = "GET".equalsIgnoreCase(method);

        if (path.startsWith("/api/auth/")) {
            if (get) {
                return null;
            }
            switch (path) {
                case "/api/auth/login": return LOGIN;
                case "/api/auth/register": return REGISTER;
                case "/api/auth/send-otp": return SEND_OTP;
                case "/api/auth/verify-otp": return VERIFY_OTP;
                case "/api/auth/reset-password": return RESET_PASSWORD;
                case "/api/auth/refresh":
                case "/api/auth/logout":
                    return null;
                default:
                    return path.startsWith("/api/auth/google/") ? GOOGLE : AUTH_OTHER;
            }
        }
        if (path.equals("/api/faq") || path.startsWith("/api/faq/")) {
            return FAQ;
        }
        if (path.equals("/api/ivr") || path.startsWith("/api/ivr/")) {
            return IVR;
        }
        if (!post) {
            return null;
        }
        if (path.equals("/api/requests/ai-draft")) {
            return AI_DRAFT;
        }
        if (path.equals("/api/ai/poster-copy")) {
            return POSTER_COPY;
        }
        if (path.equals("/api/requests")) {
            return REQUEST_CREATE;
        }
        if (path.equals("/api/contact")) {
            return CONTACT;
        }
        if (CIRCLE_RESPOND_PATH.matcher(path).matches()) {
            return CIRCLE_RESPOND;
        }
        if (REQUEST_REPORT_PATH.matcher(path).matches()) {
            return REQUEST_REPORT;
        }
        return null;
    }

    /** The invite token of {@code /api/circle/{token}/respond}, or {@code null}. */
    public static String circleTokenOf(String path) {
        if (path == null) {
            return null;
        }
        java.util.regex.Matcher m = CIRCLE_RESPOND_PATH.matcher(path);
        return m.matches() ? m.group(1) : null;
    }
}
