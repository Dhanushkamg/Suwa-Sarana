package com.suwasarana.api.auth.token;

import com.suwasarana.api.auth.AuthProperties;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Issues and spends single-use tokens. The raw token (256 random bits, URL-safe) is returned once, to be put in
 * an email; the database keeps only its SHA-256 hash, so a database leak does not yield usable links.
 */
@Service
public class AuthTokenService {

    private final AuthTokenRepository repository;
    private final AuthProperties properties;
    private final SecureRandom random = new SecureRandom();

    public AuthTokenService(AuthTokenRepository repository, AuthProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    /** A new email-verification token for the user; any earlier open one stops working. */
    public String issueEmailVerification(Long userId) {
        return issue(AuthTokenType.EMAIL_VERIFY, userId, null, null, Duration.ofHours(properties.getEmailVerificationTtlHours()));
    }

    /** A new password-reset token for the user; any earlier open one stops working. */
    public String issuePasswordReset(Long userId) {
        return issue(AuthTokenType.PASSWORD_RESET, userId, null, null, Duration.ofMinutes(properties.getPasswordResetTtlMinutes()));
    }

    /** An administrator invitation bound to an email address. */
    public String issueAdminInvite(String email, Long createdBy) {
        repository.invalidateOpenForEmail(email, AuthTokenType.ADMIN_INVITE, Instant.now());
        return issue(AuthTokenType.ADMIN_INVITE, null, email, createdBy, Duration.ofHours(properties.getAdminInviteTtlHours()));
    }

    private String issue(AuthTokenType type, Long userId, String email, Long createdBy, Duration ttl) {
        Instant now = Instant.now();
        if (userId != null) {
            repository.invalidateOpen(userId, type, now);
        }
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        AuthToken token = new AuthToken();
        token.setType(type);
        token.setTokenHash(sha256Hex(raw));
        token.setUserId(userId);
        token.setEmail(email);
        token.setCreatedBy(createdBy);
        token.setCreatedAt(now);
        token.setExpiresAt(now.plus(ttl));
        repository.save(token);
        return raw;
    }

    /**
     * Spends the token if it is valid: right type, never used, not expired. Returns the stored token (with its
     * user/email) on success, empty otherwise. Single-use is enforced atomically in the database.
     */
    public Optional<AuthToken> consume(AuthTokenType type, String rawToken) {
        if (rawToken == null || rawToken.isBlank() || rawToken.length() > 200) {
            return Optional.empty();
        }
        String hash = sha256Hex(rawToken.trim());
        if (repository.markUsed(hash, type, Instant.now()) != 1) {
            return Optional.empty();
        }
        return repository.findByTokenHash(hash);
    }

    /** Looks at a token without spending it: right type, unused, unexpired. */
    public Optional<AuthToken> peek(AuthTokenType type, String rawToken) {
        if (rawToken == null || rawToken.isBlank() || rawToken.length() > 200) {
            return Optional.empty();
        }
        Instant now = Instant.now();
        return repository.findByTokenHash(sha256Hex(rawToken.trim()))
                .filter(t -> t.getType() == type && t.getUsedAt() == null && t.getExpiresAt().isAfter(now));
    }

    /** Seconds until another token may be requested for this user, or 0 when allowed now. */
    public int cooldownRemainingSeconds(Long userId, AuthTokenType type) {
        return repository.findTopByUserIdAndTypeOrderByCreatedAtDesc(userId, type)
                .map(t -> {
                    long left = Duration.between(Instant.now(), t.getCreatedAt().plusSeconds(properties.getResendCooldownSeconds())).getSeconds();
                    return (int) Math.max(0, left + 1);
                })
                .orElse(0);
    }

    public static String sha256Hex(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
