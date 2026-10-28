package com.suwasarana.api.notification;

import jakarta.persistence.*;
import java.time.Instant;

/** One row of a user's persistent inbox. Holds translation keys + JSON params, never a translated sentence. */
@Entity
@Table(name = "user_notifications")
public class UserNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 40)
    private String type;

    @Column(name = "title_key", nullable = false, length = 100)
    private String titleKey;

    @Column(name = "body_key", nullable = false, length = 100)
    private String bodyKey;

    /** JSON object as text. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String params = "{}";

    @Column(name = "reference_id")
    private Long referenceId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "read_at")
    private Instant readAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getTitleKey() { return titleKey; }
    public void setTitleKey(String titleKey) { this.titleKey = titleKey; }
    public String getBodyKey() { return bodyKey; }
    public void setBodyKey(String bodyKey) { this.bodyKey = bodyKey; }
    public String getParams() { return params; }
    public void setParams(String params) { this.params = params; }
    public Long getReferenceId() { return referenceId; }
    public void setReferenceId(Long referenceId) { this.referenceId = referenceId; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getReadAt() { return readAt; }
    public void setReadAt(Instant readAt) { this.readAt = readAt; }
}
