package com.suwasarana.api.notification.dto;

import java.time.Instant;
import java.util.Map;

/**
 * A notification as the client sees it, both from the inbox endpoints and as the payload of SSE events.
 * The client localises {@code titleKey}/{@code bodyKey} with {@code params}; {@code title}/{@code body} are an
 * English rendering kept for older clients and as a last-resort fallback.
 */
public class NotificationDto {

    private Long id;
    private String type;
    private String titleKey;
    private String bodyKey;
    private Map<String, Object> params;
    private Long referenceId;
    private Instant createdAt;
    private Instant readAt;
    private String title;
    private String body;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getTitleKey() { return titleKey; }
    public void setTitleKey(String titleKey) { this.titleKey = titleKey; }
    public String getBodyKey() { return bodyKey; }
    public void setBodyKey(String bodyKey) { this.bodyKey = bodyKey; }
    public Map<String, Object> getParams() { return params; }
    public void setParams(Map<String, Object> params) { this.params = params; }
    public Long getReferenceId() { return referenceId; }
    public void setReferenceId(Long referenceId) { this.referenceId = referenceId; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getReadAt() { return readAt; }
    public void setReadAt(Instant readAt) { this.readAt = readAt; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
}
