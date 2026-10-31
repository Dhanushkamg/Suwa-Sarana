package com.suwasarana.api.request;

import com.suwasarana.api.common.Times;
import com.suwasarana.api.user.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "blood_requests")
public class BloodRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_id", nullable = false)
    private User requester;

    @Column(nullable = false, length = 3)
    private String patientBloodType;

    @Column(nullable = false)
    private short unitsNeeded = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Urgency urgency;

    @Column(nullable = false, length = 150)
    private String hospitalName;

    @Column(nullable = false, length = 50)
    private String district;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RequestStatus status = RequestStatus.OPEN;

    @Column(nullable = false)
    private short currentRadiusKm = 5;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = Times.utcNow();

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private LocalDateTime lastEscalatedAt = Times.utcNow();

    @Column
    private Integer fraudRiskScore = 0;

    @Column(length = 255)
    private String aiFlagReason;

    @Column
    private LocalDateTime triagedAt;

    /** When the request reached a terminal state (FULFILLED, CANCELLED or EXPIRED). Basis of the fulfilment rate. */
    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    /** When the request last became MATCHED; the donor-confirmation window is measured from here. */
    @Column(name = "matched_at")
    private LocalDateTime matchedAt;

    /** Why a requester closed the request (see CloseReason); null for other endings. */
    @Column(name = "close_reason", length = 30)
    private String closeReason;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public User getRequester() { return requester; }
    public void setRequester(User requester) { this.requester = requester; }
    public String getPatientBloodType() { return patientBloodType; }
    public void setPatientBloodType(String patientBloodType) { this.patientBloodType = patientBloodType; }
    public short getUnitsNeeded() { return unitsNeeded; }
    public void setUnitsNeeded(short unitsNeeded) { this.unitsNeeded = unitsNeeded; }
    public Urgency getUrgency() { return urgency; }
    public void setUrgency(Urgency urgency) { this.urgency = urgency; }
    public String getHospitalName() { return hospitalName; }
    public void setHospitalName(String hospitalName) { this.hospitalName = hospitalName; }
    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }
    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
    public RequestStatus getStatus() { return status; }
    public void setStatus(RequestStatus status) { this.status = status; }
    public short getCurrentRadiusKm() { return currentRadiusKm; }
    public void setCurrentRadiusKm(short currentRadiusKm) { this.currentRadiusKm = currentRadiusKm; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public LocalDateTime getLastEscalatedAt() { return lastEscalatedAt; }
    public void setLastEscalatedAt(LocalDateTime lastEscalatedAt) { this.lastEscalatedAt = lastEscalatedAt; }
    public Integer getFraudRiskScore() { return fraudRiskScore; }
    public void setFraudRiskScore(Integer fraudRiskScore) { this.fraudRiskScore = fraudRiskScore; }
    public String getAiFlagReason() { return aiFlagReason; }
    public void setAiFlagReason(String aiFlagReason) { this.aiFlagReason = aiFlagReason; }
    public LocalDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(LocalDateTime closedAt) { this.closedAt = closedAt; }
    public LocalDateTime getMatchedAt() { return matchedAt; }
    public void setMatchedAt(LocalDateTime matchedAt) { this.matchedAt = matchedAt; }
    public String getCloseReason() { return closeReason; }
    public void setCloseReason(String closeReason) { this.closeReason = closeReason; }
    public boolean isActive() {
        return status == RequestStatus.OPEN || status == RequestStatus.ESCALATING || status == RequestStatus.MATCHED;
    }
    public LocalDateTime getTriagedAt() { return triagedAt; }
    public void setTriagedAt(LocalDateTime triagedAt) { this.triagedAt = triagedAt; }
}
