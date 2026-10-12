package com.suwasarana.api.slot.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * Bulk-generates slots at regular intervals between overallStartTime and overallEndTime.
 * Example: 9:00–13:00, intervalMinutes=30, capacityPerSlot=4 → 8 slots of 30 min each.
 */
public class BulkCreateSlotDto {

    @NotNull(message = "Overall start time is required")
    private LocalDateTime overallStartTime;

    @NotNull(message = "Overall end time is required")
    private LocalDateTime overallEndTime;

    @NotNull(message = "Interval in minutes is required")
    @Min(value = 5, message = "Interval must be at least 5 minutes")
    @Max(value = 1440, message = "Interval must be at most 1440 minutes")
    private Integer intervalMinutes;

    @NotNull(message = "Capacity per slot is required")
    @Min(value = 1, message = "Capacity must be at least 1")
    @Max(value = 1000, message = "Capacity must be at most 1000")
    private Integer capacityPerSlot;

    public LocalDateTime getOverallStartTime() { return overallStartTime; }
    public void setOverallStartTime(LocalDateTime overallStartTime) { this.overallStartTime = overallStartTime; }

    public LocalDateTime getOverallEndTime() { return overallEndTime; }
    public void setOverallEndTime(LocalDateTime overallEndTime) { this.overallEndTime = overallEndTime; }

    public Integer getIntervalMinutes() { return intervalMinutes; }
    public void setIntervalMinutes(Integer intervalMinutes) { this.intervalMinutes = intervalMinutes; }

    public Integer getCapacityPerSlot() { return capacityPerSlot; }
    public void setCapacityPerSlot(Integer capacityPerSlot) { this.capacityPerSlot = capacityPerSlot; }
}
