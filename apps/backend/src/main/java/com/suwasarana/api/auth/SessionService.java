package com.suwasarana.api.auth;

import com.suwasarana.api.audit.AuditLogService;
import com.suwasarana.api.auth.dto.AuthResponse;
import com.suwasarana.api.common.ApiException;
import com.suwasarana.api.security.JwtTokenProvider;
import com.suwasarana.api.user.User;
import com.suwasarana.api.user.UserRepository;
import com.suwasarana.api.user.UserStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

/**
 * Sessions: short-lived access tokens (JWT, bound to the account's token version) plus rotating refresh tokens.
 *
 * Rotation: every refresh exchanges the presented token for a new one and marks the old one used. If a used token
 * is presented again after the short grace window (a second browser tab racing the first), it was copied: ALL of the
 * user's refresh tokens are revoked and the token version is bumped, which also kills outstanding access tokens.
 */
@Service
public class SessionService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final JwtTokenProvider tokenProvider;
    private final AuthProperties properties;
    private final AuditLogService auditLog;
    private final int refreshTtlDays;

    private final SecureRandom random = new SecureRandom();

    public SessionService(RefreshTokenRepository refreshTokenRepository, UserRepository userRepository,
                          JwtTokenProvider tokenProvider, AuthProperties properties, AuditLogService auditLog,
                          @Value("${jwt.refresh-token-expiry-days:7}") int refreshTtlDays) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
        this.tokenProvider = tokenProvider;
        this.properties = properties;
        this.auditLog = auditLog;
        this.refreshTtlDays = refreshTtlDays;
    }

    /** A brand-new session (login / register): access token + refresh token. */
    @Transactional
    public AuthResponse issue(User user) {
        String refresh = createRefreshToken(user);
        return AuthResponse.of(user, accessTokenFor(user), refresh);
    }

    /**
     * Exchanges a refresh token for a new session.
     * {@code noRollbackFor}: the revocation after a detected reuse must be committed even though we answer 401.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse rotate(String rawRefreshToken, String ipAddress) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw AuthException.unauthorized(AuthCodes.NOT_SIGNED_IN);
        }
        String hash = AuthService.sha256Hex(rawRefreshToken);
        RefreshToken token = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> AuthException.unauthorized(AuthCodes.SESSION_EXPIRED));
        User user = token.getUser();
        Instant now = Instant.now();

        if (token.getUsedAt() != null) {
            rejectReuse(token, user, now, ipAddress);
        }
        if (token.getExpiresAt().isBefore(now)) {
            refreshTokenRepository.delete(token);
            throw AuthException.unauthorized(AuthCodes.SESSION_EXPIRED);
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            refreshTokenRepository.deleteByUser(user);
            throw AuthException.unauthorized(AuthCodes.SESSION_EXPIRED);
        }

        // Atomic claim: if two requests present the same token at once, exactly one gets here.
        if (refreshTokenRepository.claim(hash, now) != 1) {
            throw AuthException.unauthorized(AuthCodes.SESSION_EXPIRED);
        }

        String newRefresh = createRefreshToken(user);
        auditLog.logTokenRefresh(user.getId(), ipAddress);
        return AuthResponse.of(user, accessTokenFor(user), newRefresh);
    }

    /** A used token came back. Inside the grace window it is a racing tab (refuse); later it is theft (revoke everything). */
    private void rejectReuse(RefreshToken token, User user, Instant now, String ipAddress) {
        Instant graceEnd = token.getUsedAt().plus(Duration.ofSeconds(properties.getRefreshReuseGraceSeconds()));
        if (now.isAfter(graceEnd)) {
            refreshTokenRepository.deleteByUser(user);
            userRepository.bumpTokenVersion(user.getId());
            auditLog.logRefreshTokenReuse(user.getId(), ipAddress);
        }
        throw AuthException.unauthorized(AuthCodes.SESSION_EXPIRED);
    }

    /** Signs the user out everywhere: deletes every refresh token and invalidates outstanding access tokens. */
    @Transactional
    public void revokeAll(User user) {
        refreshTokenRepository.deleteByUser(user);
        userRepository.bumpTokenVersion(user.getId());
    }

    /** Deletes just the session behind this raw refresh token (cookie-only logout). */
    @Transactional
    public void revokeToken(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(AuthService.sha256Hex(rawRefreshToken)).ifPresent(refreshTokenRepository::delete);
    }

    /** The owner of a raw refresh token, if it is a known one. */
    @Transactional(readOnly = true)
    public Optional<User> ownerOf(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return Optional.empty();
        }
        return refreshTokenRepository.findByTokenHash(AuthService.sha256Hex(rawRefreshToken)).map(RefreshToken::getUser);
    }

    public String accessTokenFor(User user) {
        return tokenProvider.generateAccessToken(user.getEmail(), user.getTokenVersion());
    }

    private String createRefreshToken(User user) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setTokenHash(AuthService.sha256Hex(raw));
        refreshToken.setExpiresAt(Instant.now().plus(Duration.ofDays(refreshTtlDays)));
        refreshTokenRepository.save(refreshToken);

        return raw; // only the hash is stored
    }
}
