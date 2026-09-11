package com.suwasarana.api.inventory;

import java.time.ZonedDateTime;

public class BloodInventoryDto {
    private String hospitalName;
    private String district;
    private String bloodType;
    private String status;
    private ZonedDateTime updatedAt;

    public String getHospitalName() { return hospitalName; }
    public void setHospitalName(String hospitalName) { this.hospitalName = hospitalName; }
    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }
    public String getBloodType() { return bloodType; }
    public void setBloodType(String bloodType) { this.bloodType = bloodType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public ZonedDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(ZonedDateTime updatedAt) { this.updatedAt = updatedAt; }
}
