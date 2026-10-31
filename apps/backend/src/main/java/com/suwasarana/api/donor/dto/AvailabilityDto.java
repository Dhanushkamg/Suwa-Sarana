package com.suwasarana.api.donor.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotNull;

public class AvailabilityDto {
    /** JSON key {@code isAvailable} (the profile response calls the same flag {@code available}, accepted here too). */
    @NotNull(message = "Availability status is required")
    @JsonAlias("available")
    private Boolean isAvailable;

    public Boolean getIsAvailable() { return isAvailable; }
    public void setIsAvailable(Boolean isAvailable) { this.isAvailable = isAvailable; }
}
