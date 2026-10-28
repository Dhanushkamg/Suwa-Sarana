package com.suwasarana.api.donor.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import com.suwasarana.api.geo.GeoBounds;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

public class UpdateDonorProfileDto {

    @NotBlank(message = "Blood type is required")
    @Pattern(regexp = "^(A|B|AB|O)[+-]$", message = "Invalid blood type")
    private String bloodType;

    @NotNull(message = "Date of birth is required")
    @Past(message = "Date of birth must be in the past")
    private LocalDate dateOfBirth;

    // NBTS: donors must weigh above 50 kg (DeferralRulesConfig.MIN_DONOR_WEIGHT_KG); the profile only stores donors who can give
    @DecimalMin(value = "50.0", inclusive = false, message = "Weight must be above 50 kg to donate blood")
    @DecimalMax(value = "300.0", message = "Weight must be at most 300 kg")
    private BigDecimal weightKg;

    @NotBlank(message = "District is required")
    @Size(max = 50, message = "District is too long")
    private String district;

    @NotNull(message = "Latitude is required")
    @DecimalMin(value = GeoBounds.MIN_LAT_STR, message = GeoBounds.LAT_MESSAGE)
    @DecimalMax(value = GeoBounds.MAX_LAT_STR, message = GeoBounds.LAT_MESSAGE)
    private Double latitude;

    @NotNull(message = "Longitude is required")
    @DecimalMin(value = GeoBounds.MIN_LON_STR, message = GeoBounds.LON_MESSAGE)
    @DecimalMax(value = GeoBounds.MAX_LON_STR, message = GeoBounds.LON_MESSAGE)
    private Double longitude;

    private LocalTime quietHoursStart;
    private LocalTime quietHoursEnd;

    // Getters and Setters
    public String getBloodType() { return bloodType; }
    public void setBloodType(String bloodType) { this.bloodType = bloodType; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }
    public BigDecimal getWeightKg() { return weightKg; }
    public void setWeightKg(BigDecimal weightKg) { this.weightKg = weightKg; }
    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }
    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
    public LocalTime getQuietHoursStart() { return quietHoursStart; }
    public void setQuietHoursStart(LocalTime quietHoursStart) { this.quietHoursStart = quietHoursStart; }
    public LocalTime getQuietHoursEnd() { return quietHoursEnd; }
    public void setQuietHoursEnd(LocalTime quietHoursEnd) { this.quietHoursEnd = quietHoursEnd; }
}
