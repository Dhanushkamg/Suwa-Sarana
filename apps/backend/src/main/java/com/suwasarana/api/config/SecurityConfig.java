package com.suwasarana.api.config;

import com.suwasarana.api.security.JwtAuthFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.suwasarana.api.common.ErrorCodes;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /** Pure JSON API: nothing may be loaded, framed or navigated to from an API response. */
    static final String API_CSP = "default-src 'none'; frame-ancestors 'none'";
    /** Swagger UI (ADMIN only) needs its own scripts/styles/images, but still no framing and no third-party origins. */
    static final String SWAGGER_CSP = "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; "
            + "img-src 'self' data:; font-src 'self' data:; connect-src 'self'; frame-ancestors 'none'";
    static final String PERMISSIONS_POLICY = "accelerometer=(), camera=(), geolocation=(), gyroscope=(), magnetometer=(), "
            + "microphone=(), payment=(), usb=()";

    private final JwtAuthFilter jwtAuthFilter;
    private final RateLimitingFilter rateLimitingFilter;

    @Autowired
    public SecurityConfig(JwtAuthFilter jwtAuthFilter, RateLimitingFilter rateLimitingFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.rateLimitingFilter = rateLimitingFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configure(http)) // delegates to CorsConfig
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // Missing/expired JWT must answer 401 (not Spring's default 403) so the frontend knows to refresh the token.
            // Same JSON envelope (with a machine-readable code) as every other error, instead of an empty body.
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, e) ->
                        ApiErrorWriter.write(response, HttpStatus.UNAUTHORIZED, ErrorCodes.AUTH_UNAUTHORIZED, "Authentication required"))
                .accessDeniedHandler((request, response, e) ->
                        ApiErrorWriter.write(response, HttpStatus.FORBIDDEN, ErrorCodes.AUTH_FORBIDDEN, "Access denied")))
            .headers(headers -> {
                RequestMatcher swagger = new OrRequestMatcher(
                        PathPatternRequestMatcher.withDefaults().matcher("/swagger-ui/**"),
                        PathPatternRequestMatcher.withDefaults().matcher("/swagger-ui.html"),
                        PathPatternRequestMatcher.withDefaults().matcher("/v3/api-docs/**"));
                headers
                    .contentTypeOptions(Customizer.withDefaults())                               // X-Content-Type-Options: nosniff
                    .frameOptions(frame -> frame.deny())                                         // X-Frame-Options: DENY
                    .referrerPolicy(ref -> ref.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                    // Only sent on HTTPS requests (Spring checks the - proxy-corrected - scheme), so plain-http localhost is unaffected.
                    .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31_536_000))
                    .addHeaderWriter(new StaticHeadersWriter("Permissions-Policy", PERMISSIONS_POLICY))
                    .addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(
                            new NegatedRequestMatcher(swagger), new StaticHeadersWriter("Content-Security-Policy", API_CSP)))
                    .addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(
                            swagger, new StaticHeadersWriter("Content-Security-Policy", SWAGGER_CSP)));
            })
            .authorizeHttpRequests(auth -> auth
                // The only /api/auth endpoint that needs a signed-in user (must precede the permitAll below)
                .requestMatchers("/api/auth/resend-verification").authenticated()
                // Public auth endpoints
                .requestMatchers("/api/auth/**").permitAll()
                // Public read-only data endpoints
                .requestMatchers("/api/circle/**").permitAll()
                .requestMatchers("/api/faq/**").permitAll()
                .requestMatchers("/api/inventory/**").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/meta/**").permitAll()
                .requestMatchers("/api/contact/**").permitAll()
                .requestMatchers("/api/ivr/**").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/camps/recommended").hasRole("DONOR")
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/camps/my-camps").hasAnyRole("HOSPITAL_REQUESTER", "BLOOD_BANK_REQUESTER")
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/camps/**").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/camps/*/slots").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/hospitals/*/slots").permitAll()
                // Actuator: only the liveness/readiness probes (status only, for the load balancer / orchestrator) are
                // public; everything else - including the aggregate /actuator/health with details, metrics, info - is ADMIN.
                .requestMatchers("/actuator/health/liveness", "/actuator/health/readiness").permitAll()
                .requestMatchers("/actuator/**").hasRole("ADMIN")
                // Swagger UI: restricted to ADMIN to prevent API schema leakage in production.
                // For local development, log in as admin@test.com first, then visit /swagger-ui/index.html
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").hasRole("ADMIN")
                // Every other request requires a valid JWT
                .anyRequest().authenticated()
            );

        // Rate limiting runs before JWT filter to block abusive IPs before token validation
        http.addFilterBefore(rateLimitingFilter, UsernamePasswordAuthenticationFilter.class);
        http.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
