package com.suwasarana.api.admin.dto;

import com.suwasarana.api.audit.AdminAuditLog;

import java.time.Instant;

public class AuditEntryDto {
    private Long id;
    private Long actorId;
    private String actorRole;
    private String action;
    private Long targetUserId;
    private String reason;
    private String details;
    private String ipAddress;
    private Instant createdAt;

    public static AuditEntryDto of(AdminAuditLog row) {
        AuditEntryDto d = new AuditEntryDto();
        d.id = row.getId();
        d.actorId = row.getActorId();
        d.actorRole = row.getActorRole();
        d.action = row.getAction();
        d.targetUserId = row.getTargetUserId();
        d.reason = row.getReason();
        d.details = row.getDetails();
        d.ipAddress = row.getIpAddress();
        d.createdAt = row.getCreatedAt();
        return d;
    }

    public Long getId() { return id; }
    public Long getActorId() { return actorId; }
    public String getActorRole() { return actorRole; }
    public String getAction() { return action; }
    public Long getTargetUserId() { return targetUserId; }
    public String getReason() { return reason; }
    public String getDetails() { return details; }
    public String getIpAddress() { return ipAddress; }
    public Instant getCreatedAt() { return createdAt; }
}
