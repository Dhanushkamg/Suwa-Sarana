package com.suwasarana.api.circle.dto;

import java.time.LocalDateTime;

public class CircleInviteDto {
    private Long requestId;
    private String inviteToken;
    private String inviteUrl;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;

    public CircleInviteDto() {}

    public CircleInviteDto(Long requestId, String inviteToken, String inviteUrl, LocalDateTime createdAt, LocalDateTime expiresAt) {
        this.requestId = requestId;
        this.inviteToken = inviteToken;
        this.inviteUrl = inviteUrl;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public Long getRequestId() { return requestId; }
    public void setRequestId(Long requestId) { this.requestId = requestId; }

    public String getInviteToken() { return inviteToken; }
    public void setInviteToken(String inviteToken) { this.inviteToken = inviteToken; }

    public String getInviteUrl() { return inviteUrl; }
    public void setInviteUrl(String inviteUrl) { this.inviteUrl = inviteUrl; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
}
