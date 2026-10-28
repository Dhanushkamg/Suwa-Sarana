package com.suwasarana.api.audit;

/** Privileged actions that are written to the persisted admin_audit_log. */
public enum AuditAction {
    VERIFICATION_APPROVED,
    VERIFICATION_REJECTED,
    USER_SUSPENDED,
    USER_RESTORED,
    ROLE_CHANGED,
    DATA_EXPORT,
    ADMIN_INVITED,
    ADMIN_INVITE_ACCEPTED,
    ADMIN_BOOTSTRAP_INVITED,
    ACCOUNT_DELETED,
    GOOGLE_ACCOUNT_TAKEOVER
}
