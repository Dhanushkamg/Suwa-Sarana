package com.suwasarana.api.request.dto;

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
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    
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

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
}
