package com.suwasarana.api.ai.dto;

import com.suwasarana.api.request.Urgency;

public class AiDraftResponseDto {
    private String patientBloodType;
    private Short unitsNeeded;
    private Urgency urgency;
    private String hospitalName;
    private String district;
    private Double latitude;
    private Double longitude;
    private boolean extractedSuccessfully;
    private String message;

    public AiDraftResponseDto() {
    }

    public String getPatientBloodType() {
        return patientBloodType;
    }

    public void setPatientBloodType(String patientBloodType) {
        this.patientBloodType = patientBloodType;
    }

    public Short getUnitsNeeded() {
        return unitsNeeded;
    }

    public void setUnitsNeeded(Short unitsNeeded) {
        this.unitsNeeded = unitsNeeded;
    }

    public Urgency getUrgency() {
        return urgency;
    }

    public void setUrgency(Urgency urgency) {
        this.urgency = urgency;
    }

    public String getHospitalName() {
        return hospitalName;
    }

    public void setHospitalName(String hospitalName) {
        this.hospitalName = hospitalName;
    }

    public String getDistrict() {
        return district;
    }

    public void setDistrict(String district) {
        this.district = district;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public boolean isExtractedSuccessfully() {
        return extractedSuccessfully;
    }

    public void setExtractedSuccessfully(boolean extractedSuccessfully) {
        this.extractedSuccessfully = extractedSuccessfully;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
