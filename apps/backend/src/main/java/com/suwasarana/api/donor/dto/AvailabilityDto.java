package com.suwasarana.api.donor.dto;

import jakarta.validation.constraints.NotNull;

public class AvailabilityDto {
    @NotNull(message = "Availability status is required")
    private Boolean isAvailable;

    public Boolean getIsAvailable() { return isAvailable; }
    public void setIsAvailable(Boolean isAvailable) { this.isAvailable = isAvailable; }
}
