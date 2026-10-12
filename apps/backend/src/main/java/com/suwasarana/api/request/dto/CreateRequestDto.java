package com.suwasarana.api.request.dto;

import com.suwasarana.api.request.Urgency;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class CreateRequestDto {

    @NotBlank
    @Pattern(regexp = "^(A|B|AB|O)[+-]$", message = "Invalid blood type")
    private String patientBloodType;

    @Min(1)
    @Max(10)
    private short unitsNeeded;

    @NotNull
    private Urgency urgency;

    @NotBlank
    @Size(max = 150)
    private String hospitalName;

    @NotBlank
    @Size(max = 50)
    private String district;

    @NotNull
    @DecimalMin("-90.0")
    @DecimalMax("90.0")
    private Double latitude;

    @NotNull
    @DecimalMin("-180.0")
    @DecimalMax("180.0")
    private Double longitude;

    // Getters and Setters
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
}
