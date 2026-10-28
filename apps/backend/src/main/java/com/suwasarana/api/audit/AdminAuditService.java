package com.suwasarana.api.audit;

import com.suwasarana.api.user.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists privileged actions to admin_audit_log: who (actor, role), what, to whom, why, when, from which IP.
 * It joins the caller's transaction, so an action and its audit row succeed or fail together.
 */
@Service
public class AdminAuditService {

    private final AdminAuditLogRepository repository;
    private final AuditLogService logger;

    public AdminAuditService(AdminAuditLogRepository repository, AuditLogService logger) {
        this.repository = repository;
        this.logger = logger;
    }

    @Transactional
    public AdminAuditLog record(Long actorId, Role actorRole, AuditAction action, Long targetUserId,
                                String reason, String details, String ipAddress) {
        AdminAuditLog row = new AdminAuditLog();
        row.setActorId(actorId);
        row.setActorRole(actorRole == null ? null : actorRole.name());
        row.setAction(action.name());
        row.setTargetUserId(targetUserId);
        row.setReason(truncate(reason, 500));
        row.setDetails(truncate(details, 1000));
        row.setIpAddress(truncate(ipAddress, 64));
        AdminAuditLog saved = repository.save(row);
        logger.logAdminAction(actorId, action.name(), targetUserId, null);
        return saved;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        String v = value.trim();
        return v.length() > max ? v.substring(0, max) : v;
    }
}
