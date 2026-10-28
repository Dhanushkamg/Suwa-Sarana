package com.suwasarana.api.circle;

import com.suwasarana.api.request.BloodRequest;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A private invite circle of one blood request. The invite token itself is never stored in clear:
 * {@code inviteTokenHash} (SHA-256) is used to look the circle up and {@code inviteTokenEnc} (AES-GCM) lets the
 * requester retrieve the same link again. The legacy plaintext column {@code invite_token} is intentionally not
 * mapped (see migration V40 and {@link CircleTokenMigrator}).
 */
@Entity
@Table(name = "request_circles")
public class RequestCircle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", nullable = false)
    private BloodRequest request;

    @Column(name = "invite_token_hash", length = 64, unique = true, nullable = false)
    private String inviteTokenHash;

    @Column(name = "invite_token_enc", length = 512)
    private String inviteTokenEnc;

    /** The clear token; only present on the instance that just created it, never persisted. */
    @Transient
    private String inviteToken;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @OneToMany(mappedBy = "circle", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CircleResponse> responses = new ArrayList<>();

    public RequestCircle() {}

    public RequestCircle(BloodRequest request, String inviteTokenHash, String inviteTokenEnc, LocalDateTime expiresAt) {
        this.request = request;
        this.inviteTokenHash = inviteTokenHash;
        this.inviteTokenEnc = inviteTokenEnc;
        this.expiresAt = expiresAt;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public BloodRequest getRequest() { return request; }
    public void setRequest(BloodRequest request) { this.request = request; }

    public String getInviteTokenHash() { return inviteTokenHash; }
    public void setInviteTokenHash(String inviteTokenHash) { this.inviteTokenHash = inviteTokenHash; }

    public String getInviteTokenEnc() { return inviteTokenEnc; }
    public void setInviteTokenEnc(String inviteTokenEnc) { this.inviteTokenEnc = inviteTokenEnc; }

    public String getInviteToken() { return inviteToken; }
    public void setInviteToken(String inviteToken) { this.inviteToken = inviteToken; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }

    public List<CircleResponse> getResponses() { return responses; }
    public void setResponses(List<CircleResponse> responses) { this.responses = responses; }

    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }
}
