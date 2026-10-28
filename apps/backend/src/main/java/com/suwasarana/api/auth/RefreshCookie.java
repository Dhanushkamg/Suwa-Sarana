package com.suwasarana.api.auth;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/** Writes the HttpOnly refresh-token cookie (path /api/auth) that carries the long-lived session. */
@Component
public class RefreshCookie {

    public static final String NAME = "refresh_token";

    // Secure=true needs HTTPS (browsers exempt localhost). SameSite must be "None" only if the
    // frontend and API live on different sites; "Lax" is right for same-site deployments.
    @Value("${app.cookie.secure:true}")
    private boolean secure;

    @Value("${app.cookie.same-site:Lax}")
    private String sameSite;

    @Value("${jwt.refresh-token-expiry-days:7}")
    private int refreshDays;

    public void set(HttpServletResponse response, String refreshToken) {
        write(response, refreshToken, (long) refreshDays * 24 * 60 * 60);
    }

    public void clear(HttpServletResponse response) {
        write(response, "", 0);
    }

    private void write(HttpServletResponse response, String value, long maxAgeSeconds) {
        ResponseCookie cookie = ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path("/api/auth")
                .maxAge(maxAgeSeconds)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
