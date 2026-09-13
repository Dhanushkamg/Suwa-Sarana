package com.suwasarana.api.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * AuditLogService writes security-sensitive events to a dedicated "AUDIT" logger.
 *
 * In production, configure your logging backend (Logback, Log4j2, etc.) to send
 * the "AUDIT" logger output to a separate, append-only audit log file or SIEM.
 *
 * IMPORTANT: This service must NEVER log passwords, JWT tokens, refresh tokens,
 * API keys, NIC numbers, raw credentials, or sensitive personal/medical data.
 */
@Service
public class AuditLogService {

    private static final Logger audit = LoggerFactory.getLogger("AUDIT");

    /** Logs a successful login event. */
    public void logLoginSuccess(Long userId, String email, String ipAddress) {
        audit.info("LOGIN_SUCCESS | userId={} | email={} | ip={}", userId, email, ipAddress);
    }

    /** Logs a failed login attempt. Does not log the attempted password. */
    public void logLoginFailure(String identifier, String ipAddress, String reason) {
        audit.warn("LOGIN_FAILURE | identifier={} | ip={} | reason={}", identifier, ipAddress, reason);
    }

    /** Logs a user logout event. */
    public void logLogout(Long userId, String ipAddress) {
        audit.info("LOGOUT | userId={} | ip={}", userId, ipAddress);
    }

    /** Logs a new user registration. */
    public void logRegistration(Long userId, String email, String role, String ipAddress) {
        audit.info("REGISTRATION | userId={} | email={} | role={} | ip={}", userId, email, role, ipAddress);
    }

    /** Logs a refresh token usage (access token renewal). */
    public void logTokenRefresh(Long userId, String ipAddress) {
        audit.info("TOKEN_REFRESH | userId={} | ip={}", userId, ipAddress);
    }

    /** Logs an unauthorized access attempt. */
    public void logUnauthorizedAccess(String identifier, String path, String ipAddress) {
        audit.warn("UNAUTHORIZED_ACCESS | identifier={} | path={} | ip={}", identifier, path, ipAddress);
    }

    /** Logs an administrative action. Notes must NOT contain sensitive data. */
    public void logAdminAction(Long adminUserId, String action, Long targetUserId, String notes) {
        audit.info("ADMIN_ACTION | adminId={} | action={} | targetUserId={} | notes={}",
                adminUserId, action, targetUserId, notes != null ? notes : "none");
    }
}
