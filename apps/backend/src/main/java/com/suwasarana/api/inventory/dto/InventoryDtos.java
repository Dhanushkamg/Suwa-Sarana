package com.suwasarana.api.inventory.dto;

import com.suwasarana.api.inventory.InventoryModel.Component;
import com.suwasarana.api.inventory.InventoryModel.StockStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

/** Request and response bodies of the blood-bank inventory API. */
public final class InventoryDtos {

    private InventoryDtos() {}

    // ------------------------------------------------------------------ requests

    public static class RecordCollectionRequest {
        @NotNull @Pattern(regexp = "^(A|B|AB|O)[+-]$", message = "Invalid blood type")
        private String bloodType;
        @NotNull
        private Component component;
        @NotNull @Min(value = 1, message = "Units must be at least 1") @Max(value = 100000, message = "Too many units")
        private Integer units;
        /** Defaults to today. */
        private LocalDate collectedOn;
        /** Shelf life is set by the institution / NBTS, so the expiry date is always supplied. */
        @NotNull(message = "Expiry date is required")
        private LocalDate expiresOn;

        public String getBloodType() { return bloodType; }
        public void setBloodType(String bloodType) { this.bloodType = bloodType; }
        public Component getComponent() { return component; }
        public void setComponent(Component component) { this.component = component; }
        public Integer getUnits() { return units; }
        public void setUnits(Integer units) { this.units = units; }
        public LocalDate getCollectedOn() { return collectedOn; }
        public void setCollectedOn(LocalDate collectedOn) { this.collectedOn = collectedOn; }
        public LocalDate getExpiresOn() { return expiresOn; }
        public void setExpiresOn(LocalDate expiresOn) { this.expiresOn = expiresOn; }
    }

    public static class UpdateBatchRequest {
        @NotNull @Min(value = 0, message = "Units cannot be negative") @Max(value = 100000, message = "Too many units")
        private Integer units;
        private LocalDate expiresOn;
        @Size(max = 255)
        private String note;

        public Integer getUnits() { return units; }
        public void setUnits(Integer units) { this.units = units; }
        public LocalDate getExpiresOn() { return expiresOn; }
        public void setExpiresOn(LocalDate expiresOn) { this.expiresOn = expiresOn; }
        public String getNote() { return note; }
        public void setNote(String note) { this.note = note; }
    }

    public static class IssueRequest {
        @NotNull @Pattern(regexp = "^(A|B|AB|O)[+-]$", message = "Invalid blood type")
        private String bloodType;
        @NotNull
        private Component component;
        @NotNull @Min(value = 1, message = "Units must be at least 1") @Max(value = 100000, message = "Too many units")
        private Integer units;
        /** Optional link to the blood request this stock was issued for. */
        private Long requestId;
        @Size(max = 255)
        private String note;

        public String getBloodType() { return bloodType; }
        public void setBloodType(String bloodType) { this.bloodType = bloodType; }
        public Component getComponent() { return component; }
        public void setComponent(Component component) { this.component = component; }
        public Integer getUnits() { return units; }
        public void setUnits(Integer units) { this.units = units; }
        public Long getRequestId() { return requestId; }
        public void setRequestId(Long requestId) { this.requestId = requestId; }
        public String getNote() { return note; }
        public void setNote(String note) { this.note = note; }
    }

    public static class ThresholdRequest {
        @NotNull @Pattern(regexp = "^(A|B|AB|O)[+-]$", message = "Invalid blood type")
        private String bloodType;
        /** Defaults to WHOLE_BLOOD. */
        private Component component;
        @NotNull @Min(0) @Max(100000)
        private Integer low;
        @NotNull @Min(0) @Max(100000)
        private Integer critical;

        public String getBloodType() { return bloodType; }
        public void setBloodType(String bloodType) { this.bloodType = bloodType; }
        public Component getComponent() { return component; }
        public void setComponent(Component component) { this.component = component; }
        public Integer getLow() { return low; }
        public void setLow(Integer low) { this.low = low; }
        public Integer getCritical() { return critical; }
        public void setCritical(Integer critical) { this.critical = critical; }
    }

    public static class SiteRequest {
        @Size(max = 150)
        private String siteName;
        @Size(max = 50)
        private String district;
        private Double latitude;
        private Double longitude;
        private Boolean publicSignals;

        public String getSiteName() { return siteName; }
        public void setSiteName(String siteName) { this.siteName = siteName; }
        public String getDistrict() { return district; }
        public void setDistrict(String district) { this.district = district; }
        public Double getLatitude() { return latitude; }
        public void setLatitude(Double latitude) { this.latitude = latitude; }
        public Double getLongitude() { return longitude; }
        public void setLongitude(Double longitude) { this.longitude = longitude; }
        public Boolean getPublicSignals() { return publicSignals; }
        public void setPublicSignals(Boolean publicSignals) { this.publicSignals = publicSignals; }
    }

    // ------------------------------------------------------------------ responses

    public static class BatchDto {
        public Long id;
        public String bloodType;
        public Component component;
        public int units;
        public LocalDate collectedOn;
        public LocalDate expiresOn;
        public String status;
        public long daysToExpiry;
    }

    /** Totals of one blood type and component, with the computed status. */
    public static class LineDto {
        public String bloodType;
        public Component component;
        public long units;
        public long expiringSoonUnits;
        public LocalDate nextExpiry;
        public StockStatus status;
        public Integer lowThreshold;
        public Integer criticalThreshold;
    }

    public static class SiteDto {
        public String siteName;
        public String district;
        public Double latitude;
        public Double longitude;
        public boolean publicSignals;
        public Instant confirmedAt;
    }

    public static class OverviewDto {
        public SiteDto site;
        public Instant asOf;
        public int expiringSoonDays;
        public List<LineDto> lines;
        public List<BatchDto> batches;
    }

    public static class ThresholdDto {
        public Long id;
        public String bloodType;
        public Component component;
        public int low;
        public int critical;
    }

    public static class MovementDto {
        public Long id;
        public Long unitId;
        public String bloodType;
        public Component component;
        public int delta;
        public String reason;
        public Long requestId;
        public String note;
        public Instant createdAt;
    }

    /**
     * One row of the public ticker. The first five fields are the shape the landing-page ticker already reads;
     * {@code updatedAt} says how fresh the institution's stock record is.
     */
    public static class StockSignalDto {
        public String hospitalName;
        public String district;
        public String bloodType;
        public String status;
        public ZonedDateTime updatedAt;
    }
}
