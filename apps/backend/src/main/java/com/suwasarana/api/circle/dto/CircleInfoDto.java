package com.suwasarana.api.circle.dto;

import com.suwasarana.api.common.Times;
import java.time.Instant;

import com.suwasarana.api.request.Urgency;
import java.time.LocalDateTime;

public class CircleInfoDto {
    private String inviteToken;
    private String patientBloodType;
    private short unitsNeeded;
    private Urgency urgency;
    private String hospitalName;
    private String district;
    private Instant expiresAt;
    private boolean isExpired;

    public CircleInfoDto() {}

    public CircleInfoDto(String inviteToken, String patientBloodType, short unitsNeeded,
                         Urgency urgency, String hospitalName, String district,
                         LocalDateTime expiresAt, boolean isExpired) {
        this.inviteToken = inviteToken;
        this.patientBloodType = patientBloodType;
        this.unitsNeeded = unitsNeeded;
        this.urgency = urgency;
        this.hospitalName = hospitalName;
        this.district = district;
        this.expiresAt = Times.instantOf(expiresAt);
        this.isExpired = isExpired;
    }

    public String getInviteToken() { return inviteToken; }
    public void setInviteToken(String inviteToken) { this.inviteToken = inviteToken; }

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

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = Times.instantOf(expiresAt); }

    public boolean isExpired() { return isExpired; }
    public void setExpired(boolean expired) { isExpired = expired; }
}
