package com.suwasarana.api.auth;

import com.suwasarana.api.audit.AuditLogService;
import com.suwasarana.api.user.User;
import com.suwasarana.api.user.UserRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Per-account brute-force protection, independent of the client IP (so a botnet cannot spread guesses over many IPs).
 *
 * Every wrong password increments {@code failed_login_count}. Each time the count reaches a multiple of the threshold
 * (default 5) the account is locked for base * 2^(n-1) seconds (60 s, 120 s, 240 s ... capped at 1 h). While locked,
 * login answers 429 {@code auth.account.locked} with Retry-After, even for the right password, and does not count
 * further attempts. A successful login or a password reset clears the counter.
 */
@Service
public class LoginAttemptService {

    private final UserRepository userRepository;
    private final AuthProperties properties;
    private final AuditLogService auditLog;

    public LoginAttemptService(UserRepository userRepository, AuthProperties properties, AuditLogService auditLog) {
        this.userRepository = userRepository;
        this.properties = properties;
        this.auditLog = auditLog;
    }

    /** @throws AuthException 429 auth.account.locked (with the seconds to wait) while the lock runs. */
    public void assertNotLocked(User user, String ipAddress) {
        Instant until = user.getLockedUntil();
        Instant now = Instant.now();
        if (until != null && until.isAfter(now)) {
            auditLog.logAccountLocked(user.getId(), ipAddress);
            long seconds = Duration.between(now, until).toSeconds() + 1;
            throw AuthException.tooManyRequests(AuthCodes.ACCOUNT_LOCKED, (int) seconds);
        }
    }

    /** Records a wrong password; locks the account when the count reaches the next threshold. */
    public void recordFailure(User user) {
        userRepository.incrementFailedLogins(user.getId());
        int failures = userRepository.findById(user.getId()).map(User::getFailedLoginCount).orElse(0);
        Duration lock = lockDuration(failures, properties.getLockout());
        if (!lock.isZero()) {
            userRepository.setLockedUntil(user.getId(), Instant.now().plus(lock));
        }
    }

    /** Clears the failure counter and any lock (successful login, password reset). */
    public void recordSuccess(User user) {
        if (user.getFailedLoginCount() != 0 || user.getLockedUntil() != null) {
            userRepository.clearLoginFailures(user.getId());
        }
    }

    /** Lock length after {@code failures} consecutive failures; zero when no lock is due yet. */
    static Duration lockDuration(int failures, AuthProperties.Lockout cfg) {
        int threshold = Math.max(1, cfg.getThreshold());
        if (failures < threshold || failures % threshold != 0) {
            return Duration.ZERO;
        }
        int step = failures / threshold - 1; // 0 for the first lock
        long seconds = (long) cfg.getBaseSeconds() << Math.min(step, 20);
        return Duration.ofSeconds(Math.min(seconds, cfg.getMaxSeconds()));
    }
}
