package com.suwasarana.api.admin.dto;

import java.time.Instant;

/** Admin view of an abuse report. Exposes only the reporter's id, never the user entity. */
public class ReportDto {

    private Long id;
    private Long requestId;
    private String reason;
    private Instant createdAt;
    private Long reporterId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getRequestId() { return requestId; }
    public void setRequestId(Long requestId) { this.requestId = requestId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Long getReporterId() { return reporterId; }
    public void setReporterId(Long reporterId) { this.reporterId = reporterId; }
}
