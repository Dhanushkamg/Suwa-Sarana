package com.suwasarana.api.donor.dto;

import com.suwasarana.api.matching.MatchStatus;
import com.suwasarana.api.request.RequestStatus;
import com.suwasarana.api.request.Urgency;
import java.time.LocalDateTime;

/** Donor-facing view of a match. Deliberately excludes donor/user/requester details. */
public class MatchDto {

    private Long id;
    private MatchStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime respondedAt;
    private RequestSummary request;

    public static class RequestSummary {
        private Long id;
        private String patientBloodType;
        private Urgency urgency;
        private String hospitalName;
        private String district;
        private short unitsNeeded;
        private RequestStatus status;
        private LocalDateTime expiresAt;
        private Double latitude;
        private Double longitude;
        private short currentRadiusKm;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getPatientBloodType() { return patientBloodType; }
        public void setPatientBloodType(String patientBloodType) { this.patientBloodType = patientBloodType; }
        public Urgency getUrgency() { return urgency; }
        public void setUrgency(Urgency urgency) { this.urgency = urgency; }
        public String getHospitalName() { return hospitalName; }
        public void setHospitalName(String hospitalName) { this.hospitalName = hospitalName; }
        public String getDistrict() { return district; }
        public void setDistrict(String district) { this.district = district; }
        public short getUnitsNeeded() { return unitsNeeded; }
        public void setUnitsNeeded(short unitsNeeded) { this.unitsNeeded = unitsNeeded; }
        public RequestStatus getStatus() { return status; }
        public void setStatus(RequestStatus status) { this.status = status; }
        public LocalDateTime getExpiresAt() { return expiresAt; }
        public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
        public Double getLatitude() { return latitude; }
        public void setLatitude(Double latitude) { this.latitude = latitude; }
        public Double getLongitude() { return longitude; }
        public void setLongitude(Double longitude) { this.longitude = longitude; }
        public short getCurrentRadiusKm() { return currentRadiusKm; }
        public void setCurrentRadiusKm(short currentRadiusKm) { this.currentRadiusKm = currentRadiusKm; }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public MatchStatus getStatus() { return status; }
    public void setStatus(MatchStatus status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getRespondedAt() { return respondedAt; }
    public void setRespondedAt(LocalDateTime respondedAt) { this.respondedAt = respondedAt; }
    public RequestSummary getRequest() { return request; }
    public void setRequest(RequestSummary request) { this.request = request; }
}
