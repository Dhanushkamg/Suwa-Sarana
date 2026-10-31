package com.suwasarana.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * CORS allow-list for the browser frontend(s). Credentials (the refresh-token cookie) are enabled, therefore the list
 * must contain exact origins: a wildcard is rejected at start-up in every profile (browsers refuse
 * {@code Access-Control-Allow-Origin: *} together with credentials, and a reflected wildcard would be a vulnerability).
 */
@Configuration
public class CorsConfig {

    /** Comma-separated origins, e.g. {@code https://suwasarana.lk,https://www.suwasarana.lk}. Empty = no cross-origin access. */
    @Value("${cors.allowed-origin:}")
    private String allowedOriginsConfig;

    /**
     * Parses and validates a comma-separated origin list.
     *
     * @throws IllegalStateException for wildcards (credentials are enabled), entries that are not {@code scheme://host[:port]}
     *                               or contain a path
     */
    public static List<String> parseOrigins(String config) {
        List<String> origins = new ArrayList<>();
        if (config == null) {
            return origins;
        }
        for (String raw : config.split(",")) {
            String origin = raw.trim();
            if (origin.isEmpty()) {
                continue;
            }
            if (origin.contains("*")) {
                throw new IllegalStateException(
                        "CORS_ALLOWED_ORIGIN must not contain a wildcard ('" + origin + "'): cookies/credentials are enabled, "
                                + "so list the exact origins instead.");
            }
            while (origin.endsWith("/")) {
                origin = origin.substring(0, origin.length() - 1); // browsers send Origin without a trailing slash
            }
            URI uri;
            try {
                uri = URI.create(origin);
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("CORS origin '" + origin + "' is not a valid URL.");
            }
            boolean hasPath = uri.getPath() != null && !uri.getPath().isEmpty();
            if (uri.getScheme() == null || uri.getHost() == null || hasPath || uri.getQuery() != null) {
                throw new IllegalStateException(
                        "CORS origin '" + origin + "' must look like scheme://host[:port] (no path or query).");
            }
            origins.add(origin);
        }
        return origins;
    }

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                String[] origins = parseOrigins(allowedOriginsConfig).toArray(String[]::new);
                if (origins.length == 0) {
                    return; // no browser origin is allowed to call the API cross-origin
                }
                registry.addMapping("/api/**")
                        .allowedOrigins(origins)
                        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                        // Explicitly enumerate only required request headers instead of wildcard "*"
                        .allowedHeaders(
                                "Authorization",
                                "Content-Type",
                                "Accept",
                                "Accept-Language",
                                "Origin",
                                "X-Requested-With",
                                "X-Request-Id",
                                "Last-Event-ID", // SSE replay (fetch-based stream, since EventSource cannot send Authorization)
                                "Cache-Control"
                        )
                        .exposedHeaders("Authorization", "X-Request-Id", "Retry-After")
                        .allowCredentials(true)
                        .maxAge(3600); // Cache preflight response for 1 hour
            }
        };
    }
}
