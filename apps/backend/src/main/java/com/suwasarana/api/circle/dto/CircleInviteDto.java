package com.suwasarana.api.circle.dto;

import com.suwasarana.api.common.Times;
import java.time.Instant;

import java.time.LocalDateTime;

public class CircleInviteDto {
    private Long requestId;
    private String inviteToken;
    private String inviteUrl;
    private Instant createdAt;
    private Instant expiresAt;

    public CircleInviteDto() {}

    public CircleInviteDto(Long requestId, String inviteToken, String inviteUrl, LocalDateTime createdAt, LocalDateTime expiresAt) {
        this.requestId = requestId;
        this.inviteToken = inviteToken;
        this.inviteUrl = inviteUrl;
        this.createdAt = Times.instantOf(createdAt);
        this.expiresAt = Times.instantOf(expiresAt);
    }

    public Long getRequestId() { return requestId; }
    public void setRequestId(Long requestId) { this.requestId = requestId; }

    public String getInviteToken() { return inviteToken; }
    public void setInviteToken(String inviteToken) { this.inviteToken = inviteToken; }

    public String getInviteUrl() { return inviteUrl; }
    public void setInviteUrl(String inviteUrl) { this.inviteUrl = inviteUrl; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = Times.instantOf(createdAt); }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = Times.instantOf(expiresAt); }
}
