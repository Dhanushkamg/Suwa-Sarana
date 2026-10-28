package com.suwasarana.api.camp.dto;

import com.suwasarana.api.geo.GeoBounds;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

public class CreateCampDto {
    @NotBlank
    @Size(max = 255, message = "Name must be at most 255 characters")
    private String name;

    @NotBlank
    @Size(max = 50, message = "District is too long")
    private String district;

    @NotBlank
    @Size(max = 255, message = "Location must be at most 255 characters")
    private String location;

    @DecimalMin(value = GeoBounds.MIN_LAT_STR, message = GeoBounds.LAT_MESSAGE)
    @DecimalMax(value = GeoBounds.MAX_LAT_STR, message = GeoBounds.LAT_MESSAGE)
    private Double latitude;

    @DecimalMin(value = GeoBounds.MIN_LON_STR, message = GeoBounds.LON_MESSAGE)
    @DecimalMax(value = GeoBounds.MAX_LON_STR, message = GeoBounds.LON_MESSAGE)
    private Double longitude;

    @NotNull
    private LocalDate scheduledDate;

    @NotNull
    private LocalTime startTime;

    @NotNull(message = "End time is required")
    private LocalTime endTime;

    /** Comma-separated blood types, e.g. "O-,A+". Each is checked against the eight valid types. */
    @Size(max = 100, message = "Required blood groups must be at most 100 characters")
    private String requiredBloodGroups;

    // Getters and Setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
    public LocalDate getScheduledDate() { return scheduledDate; }
    public void setScheduledDate(LocalDate scheduledDate) { this.scheduledDate = scheduledDate; }
    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }
    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }
    public String getRequiredBloodGroups() { return requiredBloodGroups; }
    public void setRequiredBloodGroups(String requiredBloodGroups) { this.requiredBloodGroups = requiredBloodGroups; }
}
