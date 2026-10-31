package com.suwasarana.api.request.dto;

import com.suwasarana.api.common.Times;
import java.time.Instant;

import com.suwasarana.api.request.RequestStatus;
import com.suwasarana.api.request.Urgency;
import java.time.LocalDateTime;

public class RequestResponseDto {
    private Long id;
    private String patientBloodType;
    private short unitsNeeded;
    private Urgency urgency;
    private String hospitalName;
    private String district;
    private Double latitude;
    private Double longitude;
    private RequestStatus status;
    private short currentRadiusKm;
    private Instant createdAt;
    private Instant expiresAt;
    private Integer fraudRiskScore;
    private String aiFlagReason;
    private Instant triagedAt;
    private Instant closedAt;
    private String closeReason;
    /** Owner/admin views only: donors who accepted, and donations confirmed so far. */
    private Long acceptedCount;
    private Long confirmedDonations;
    /** True when latitude/longitude are rounded (about 1 km) because the viewer is not the owner/admin/matched donor. */
    private boolean coarseLocation;

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

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

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = Times.instantOf(createdAt); }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = Times.instantOf(expiresAt); }

    public Integer getFraudRiskScore() { return fraudRiskScore; }
    public void setFraudRiskScore(Integer fraudRiskScore) { this.fraudRiskScore = fraudRiskScore; }

    public String getAiFlagReason() { return aiFlagReason; }
    public void setAiFlagReason(String aiFlagReason) { this.aiFlagReason = aiFlagReason; }

    public Instant getTriagedAt() { return triagedAt; }
    public void setTriagedAt(LocalDateTime triagedAt) { this.triagedAt = Times.instantOf(triagedAt); }

    public Instant getClosedAt() { return closedAt; }
    public void setClosedAt(LocalDateTime closedAt) { this.closedAt = Times.instantOf(closedAt); }

    public String getCloseReason() { return closeReason; }
    public void setCloseReason(String closeReason) { this.closeReason = closeReason; }

    public Long getAcceptedCount() { return acceptedCount; }
    public void setAcceptedCount(Long acceptedCount) { this.acceptedCount = acceptedCount; }

    public Long getConfirmedDonations() { return confirmedDonations; }
    public void setConfirmedDonations(Long confirmedDonations) { this.confirmedDonations = confirmedDonations; }

    public boolean isCoarseLocation() { return coarseLocation; }
    public void setCoarseLocation(boolean coarseLocation) { this.coarseLocation = coarseLocation; }
}
