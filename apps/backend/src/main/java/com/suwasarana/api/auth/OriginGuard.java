package com.suwasarana.api.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * CSRF defence for the endpoints driven by the refresh cookie (/auth/refresh, /auth/logout). The cookie is HttpOnly,
 * so JavaScript on another site cannot read it, but a browser would still attach it to a cross-site request.
 *
 * Rule (no token needed): if the browser says where the request comes from, that place must be ours.
 *  - {@code Origin} present: must be one of the configured frontend origins (cors.allowed-origin, comma separated);
 *  - else {@code Sec-Fetch-Site} present: must be same-origin, same-site or none;
 *  - neither header (curl, server-to-server): allowed, since a cross-site browser attack always carries one of them.
 */
@Component
public class OriginGuard {

    private final Set<String> allowedOrigins;

    public OriginGuard(@Value("${cors.allowed-origin:http://localhost:3000}") String allowedOrigin) {
        this.allowedOrigins = Arrays.stream(allowedOrigin.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(OriginGuard::stripTrailingSlash)
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }

    /** @throws AuthException 403 auth.origin.blocked */
    public void check(HttpServletRequest request) {
        if (!isAllowed(request.getHeader("Origin"), request.getHeader("Sec-Fetch-Site"))) {
            throw AuthException.forbidden(AuthCodes.ORIGIN_BLOCKED);
        }
    }

    boolean isAllowed(String origin, String fetchSite) {
        if (origin != null && !origin.isBlank()) {
            return allowedOrigins.contains(stripTrailingSlash(origin.trim()).toLowerCase(Locale.ROOT));
        }
        if (fetchSite != null && !fetchSite.isBlank()) {
            String site = fetchSite.trim().toLowerCase(Locale.ROOT);
            return site.equals("same-origin") || site.equals("same-site") || site.equals("none");
        }
        return true;
    }

    private static String stripTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
