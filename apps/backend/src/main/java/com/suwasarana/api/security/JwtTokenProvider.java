package com.suwasarana.api.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

/**
 * Issues and checks short-lived access tokens. Besides the subject (email) every token carries the account's
 * {@code tv} (token version) claim; JwtAuthFilter rejects a token whose {@code tv} is older than the account's
 * current value, which is how password changes / resets / sign-out-everywhere kill outstanding access tokens.
 */
@Component
public class JwtTokenProvider {

    public static final String TOKEN_VERSION_CLAIM = "tv";

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.access-token-expiry-minutes}")
    private int jwtExpirationInMinutes;

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /** Preferred entry point: token for {@code subject} (email) bound to the account's current token version. */
    public String generateAccessToken(String subject, int tokenVersion) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + (long) jwtExpirationInMinutes * 60 * 1000);

        return Jwts.builder()
                .subject(subject)
                .claim(TOKEN_VERSION_CLAIM, tokenVersion)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    public String generateToken(Authentication authentication) {
        UserDetails userPrincipal = (UserDetails) authentication.getPrincipal();
        int tokenVersion = userPrincipal instanceof UserDetailsImpl impl ? impl.getTokenVersion() : 0;
        return generateAccessToken(userPrincipal.getUsername(), tokenVersion);
    }

    /** @deprecated carries token version 0; use {@link #generateAccessToken(String, int)}. */
    @Deprecated
    public String generateTokenFromUsername(String username) {
        return generateAccessToken(username, 0);
    }

    /** Verified claims of a token; throws {@link JwtException} when the signature or expiry is wrong. */
    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public int tokenVersionOf(Claims claims) {
        Number tv = claims.get(TOKEN_VERSION_CLAIM, Number.class);
        return tv == null ? 0 : tv.intValue();
    }

    public String getUsernameFromJWT(String token) {
        return parseClaims(token).getSubject();
    }

    public boolean validateToken(String authToken) {
        try {
            parseClaims(authToken);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }
}
