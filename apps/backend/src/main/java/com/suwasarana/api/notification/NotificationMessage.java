package com.suwasarana.api.notification;

public class NotificationMessage {
    private String title;
    private String body;
    private String type;
    private Long referenceId; // e.g. Request ID

    public NotificationMessage() {}

    public NotificationMessage(String title, String body, String type, Long referenceId) {
        this.title = title;
        this.body = body;
        this.type = type;
        this.referenceId = referenceId;
    }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Long getReferenceId() { return referenceId; }
    public void setReferenceId(Long referenceId) { this.referenceId = referenceId; }
}
