package com.suwasarana.api.audit;

import com.suwasarana.api.email.LoggingEmailSender;
import com.suwasarana.api.user.PhoneNumbers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * AuditLogService writes security-sensitive events to a dedicated "AUDIT" logger (for a SIEM / append-only file).
 * Privileged admin actions are additionally persisted by {@link AdminAuditService}.
 *
 * IMPORTANT: this service must NEVER log passwords, tokens, OTP codes, NIC numbers, whole phone numbers or whole
 * email addresses. Accounts are identified by id; anything typed by a user is sanitised (no line breaks, length-capped)
 * so it cannot forge log lines.
 */
@Service
public class AuditLogService {

    private static final Logger audit = LoggerFactory.getLogger("AUDIT");

    /** Logs a successful login event. */
    public void logLoginSuccess(Long userId, String email, String ipAddress) {
        audit.info("LOGIN_SUCCESS | userId={} | ip={}", userId, clean(ipAddress));
    }

    /** Logs a failed login attempt. {@code identifier} is whatever was typed; it is masked and sanitised. */
    public void logLoginFailure(String identifier, String ipAddress, String reason) {
        audit.warn("LOGIN_FAILURE | identifier={} | ip={} | reason={}", maskIdentifier(identifier), clean(ipAddress), clean(reason));
    }

    /** Logs a login blocked by the per-account lockout. */
    public void logAccountLocked(Long userId, String ipAddress) {
        audit.warn("ACCOUNT_LOCKED | userId={} | ip={}", userId, clean(ipAddress));
    }

    /** Logs a user logout event. */
    public void logLogout(Long userId, String ipAddress) {
        audit.info("LOGOUT | userId={} | ip={}", userId, clean(ipAddress));
    }

    /** Logs a new user registration. */
    public void logRegistration(Long userId, String email, String role, String ipAddress) {
        audit.info("REGISTRATION | userId={} | role={} | ip={}", userId, clean(role), clean(ipAddress));
    }

    /** Logs a password reset done through phone OTP or an emailed link. */
    public void logPasswordReset(Long userId, String ipAddress) {
        audit.info("PASSWORD_RESET | userId={} | ip={}", userId, clean(ipAddress));
    }

    /** Logs a password change by a signed-in user. */
    public void logPasswordChange(Long userId, String ipAddress) {
        audit.info("PASSWORD_CHANGE | userId={} | ip={}", userId, clean(ipAddress));
    }

    /** Logs a refresh token usage (access token renewal). */
    public void logTokenRefresh(Long userId, String ipAddress) {
        audit.info("TOKEN_REFRESH | userId={} | ip={}", userId, clean(ipAddress));
    }

    /** A rotated refresh token was presented again: every session of the user has been revoked. */
    public void logRefreshTokenReuse(Long userId, String ipAddress) {
        audit.warn("REFRESH_TOKEN_REUSE | userId={} | ip={} | action=all-sessions-revoked", userId, clean(ipAddress));
    }

    /** Free-form security event about an account (e.g. EMAIL_VERIFIED, GOOGLE_LINKED). */
    public void logSecurityEvent(String event, Long userId, String ipAddress) {
        audit.info("{} | userId={} | ip={}", clean(event), userId, clean(ipAddress));
    }

    /** Logs an unauthorized access attempt. */
    public void logUnauthorizedAccess(String identifier, String path, String ipAddress) {
        audit.warn("UNAUTHORIZED_ACCESS | identifier={} | path={} | ip={}", maskIdentifier(identifier), clean(path), clean(ipAddress));
    }

    /** Logs an administrative action. Notes must NOT contain sensitive data. */
    public void logAdminAction(Long adminUserId, String action, Long targetUserId, String notes) {
        audit.info("ADMIN_ACTION | adminId={} | action={} | targetUserId={} | notes={}",
                adminUserId, clean(action), targetUserId, notes != null ? clean(notes) : "none");
    }

    // ---- helpers -----------------------------------------------------------------------------------------

    /** Email -> "a*****@host", phone -> "07*****567", anything else -> "<redacted>". */
    static String maskIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return "<empty>";
        }
        String id = identifier.trim();
        if (id.contains("@")) {
            return clean(LoggingEmailSender.maskEmail(id));
        }
        if (PhoneNumbers.normalize(id) != null) {
            return PhoneNumbers.mask(id);
        }
        return "<redacted>";
    }

    /** Removes control characters (log forging) and caps the length. */
    static String clean(String value) {
        if (value == null) {
            return "";
        }
        String s = value.replaceAll("[\\p{Cntrl}]+", " ").trim();
        return s.length() > 200 ? s.substring(0, 200) + "..." : s;
    }
}
