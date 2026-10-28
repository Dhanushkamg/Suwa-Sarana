package com.suwasarana.api.notification;

import java.util.Map;

/**
 * A notification as the callers build it. {@code titleKey}/{@code bodyKey}/{@code params} are what the inbox stores
 * and the frontend localises; {@code title}/{@code body} are an English fallback (also used for the SMS when no
 * template exists for the type). Older callers that only pass title/body keep working: they are stored under the
 * generic keys with the text carried in params.
 */
public class NotificationMessage {
    private String title;
    private String body;
    private String type;
    private Long referenceId; // e.g. Request ID
    private String titleKey;
    private String bodyKey;
    private Map<String, Object> params;

    public NotificationMessage() {}

    public NotificationMessage(String title, String body, String type, Long referenceId) {
        this.title = title;
        this.body = body;
        this.type = type;
        this.referenceId = referenceId;
    }

    /** Adds the translation keys and their parameters. */
    public NotificationMessage withKeys(String titleKey, String bodyKey, Map<String, Object> params) {
        this.titleKey = titleKey;
        this.bodyKey = bodyKey;
        this.params = params;
        return this;
    }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Long getReferenceId() { return referenceId; }
    public void setReferenceId(Long referenceId) { this.referenceId = referenceId; }
    public String getTitleKey() { return titleKey; }
    public void setTitleKey(String titleKey) { this.titleKey = titleKey; }
    public String getBodyKey() { return bodyKey; }
    public void setBodyKey(String bodyKey) { this.bodyKey = bodyKey; }
    public Map<String, Object> getParams() { return params; }
    public void setParams(Map<String, Object> params) { this.params = params; }
}
