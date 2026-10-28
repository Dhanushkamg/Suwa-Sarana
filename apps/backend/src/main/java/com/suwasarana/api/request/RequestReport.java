package com.suwasarana.api.request;

import com.suwasarana.api.user.User;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "request_reports",
       uniqueConstraints = @UniqueConstraint(name = "uq_request_reports_once", columnNames = {"request_id", "reported_by"}))
public class RequestReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", nullable = false)
    private BloodRequest request;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reported_by", nullable = false)
    private User reportedBy;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(name = "reason_code", length = 30)
    private String reasonCode;

    @Column(name = "details", length = 500)
    private String details;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public BloodRequest getRequest() { return request; }
    public void setRequest(BloodRequest request) { this.request = request; }

    public User getReportedBy() { return reportedBy; }
    public void setReportedBy(User reportedBy) { this.reportedBy = reportedBy; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getReasonCode() { return reasonCode; }
    public void setReasonCode(String reasonCode) { this.reasonCode = reasonCode; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
