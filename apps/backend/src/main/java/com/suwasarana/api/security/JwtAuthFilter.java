package com.suwasarana.api.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Authenticates a request from its Bearer access token. The account is re-read on every request, so a role change,
 * suspension or token-version bump takes effect immediately instead of when the token expires.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    @org.springframework.context.annotation.Lazy
    private UserDetailsService customUserDetailsService; // Interface provided by Spring Security

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String jwt = getJwtFromRequest(request);
        if (StringUtils.hasText(jwt)) {
            try {
                Claims claims = tokenProvider.parseClaims(jwt);
                UserDetails userDetails = customUserDetailsService.loadUserByUsername(claims.getSubject());

                if (isAcceptable(userDetails, tokenProvider.tokenVersionOf(claims))) {
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (JwtException | IllegalArgumentException | UsernameNotFoundException ex) {
                // Expired/forged/stale tokens are routine; the request simply stays anonymous (-> 401). No stack trace, no token.
                log.debug("Bearer token rejected: {}", ex.getClass().getSimpleName());
            } catch (RuntimeException ex) {
                log.error("Could not set user authentication in security context: {}", ex.getClass().getSimpleName());
            }
        }

        filterChain.doFilter(request, response);
    }

    /** Suspended/erased accounts and tokens issued before the last password change / revoke are refused. */
    private boolean isAcceptable(UserDetails userDetails, int tokenVersion) {
        if (!userDetails.isEnabled()) {
            return false;
        }
        if (userDetails instanceof UserDetailsImpl impl) {
            return tokenVersion >= impl.getTokenVersion();
        }
        return true;
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        // NOTE: Accepting JWT as a URL query parameter is intentionally NOT supported.
        // Doing so would expose tokens in server access logs, browser history, and
        // Referer headers. Tokens must only be sent in the Authorization header.
        return null;
    }
}
