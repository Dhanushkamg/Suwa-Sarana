package com.suwasarana.api.inventory;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Container for the small enums and entities of the blood-bank inventory. They are nested so the whole data model is
 * readable in one file; each maps to a table created by migration V32.
 */
public final class InventoryModel {

    private InventoryModel() {}

    public enum Component { WHOLE_BLOOD, RBC, PLATELETS, PLASMA }

    /** Computed, never stored. Precedence: CRITICAL, LOW, EXPIRING_SOON, OK. */
    public enum StockStatus { OK, LOW, CRITICAL, EXPIRING_SOON }

    public enum BatchStatus { ACTIVE, DEPLETED, EXPIRED }

    public enum MovementReason { COLLECTION, ISSUE, EXPIRY, ADJUSTMENT, DISCARD }

    /** One collection / delivery of one blood type and component. */
    @Entity(name = "InventoryUnit")
    @Table(name = "inventory_units")
    public static class Unit {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        @Column(name = "owner_user_id", nullable = false)
        private Long ownerUserId;
        @Column(name = "blood_type", nullable = false, length = 3)
        private String bloodType;
        @Enumerated(EnumType.STRING)
        @Column(nullable = false, length = 20)
        private Component component;
        @Column(nullable = false)
        private int units;
        @Column(name = "collected_on", nullable = false)
        private LocalDate collectedOn;
        @Column(name = "expires_on", nullable = false)
        private LocalDate expiresOn;
        @Enumerated(EnumType.STRING)
        @Column(nullable = false, length = 20)
        private BatchStatus status = BatchStatus.ACTIVE;
        @Column(name = "created_at", nullable = false, updatable = false)
        private Instant createdAt = Instant.now();
        @Column(name = "updated_at", nullable = false)
        private Instant updatedAt = Instant.now();

        public Long getId() { return id; }
        public Long getOwnerUserId() { return ownerUserId; }
        public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
        public String getBloodType() { return bloodType; }
        public void setBloodType(String bloodType) { this.bloodType = bloodType; }
        public Component getComponent() { return component; }
        public void setComponent(Component component) { this.component = component; }
        public int getUnits() { return units; }
        public void setUnits(int units) { this.units = units; }
        public LocalDate getCollectedOn() { return collectedOn; }
        public void setCollectedOn(LocalDate collectedOn) { this.collectedOn = collectedOn; }
        public LocalDate getExpiresOn() { return expiresOn; }
        public void setExpiresOn(LocalDate expiresOn) { this.expiresOn = expiresOn; }
        public BatchStatus getStatus() { return status; }
        public void setStatus(BatchStatus status) { this.status = status; }
        public Instant getCreatedAt() { return createdAt; }
        public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
        public Instant getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    }

    /** Levels at or below which a blood type / component is LOW or CRITICAL. Set by each institution. */
    @Entity(name = "InventoryThreshold")
    @Table(name = "inventory_thresholds",
            uniqueConstraints = @UniqueConstraint(columnNames = {"owner_user_id", "blood_type", "component"}))
    public static class Threshold {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        @Column(name = "owner_user_id", nullable = false)
        private Long ownerUserId;
        @Column(name = "blood_type", nullable = false, length = 3)
        private String bloodType;
        @Enumerated(EnumType.STRING)
        @Column(nullable = false, length = 20)
        private Component component = Component.WHOLE_BLOOD;
        @Column(name = "low_threshold", nullable = false)
        private int lowThreshold;
        @Column(name = "critical_threshold", nullable = false)
        private int criticalThreshold;
        /** LOW / CRITICAL when the owner was last alerted, null otherwise: stops repeat alerts for the same state. */
        @Column(name = "last_notified_status", length = 20)
        private String lastNotifiedStatus;
        @Column(name = "updated_at", nullable = false)
        private Instant updatedAt = Instant.now();

        public Long getId() { return id; }
        public Long getOwnerUserId() { return ownerUserId; }
        public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
        public String getBloodType() { return bloodType; }
        public void setBloodType(String bloodType) { this.bloodType = bloodType; }
        public Component getComponent() { return component; }
        public void setComponent(Component component) { this.component = component; }
        public int getLowThreshold() { return lowThreshold; }
        public void setLowThreshold(int lowThreshold) { this.lowThreshold = lowThreshold; }
        public int getCriticalThreshold() { return criticalThreshold; }
        public void setCriticalThreshold(int criticalThreshold) { this.criticalThreshold = criticalThreshold; }
        public String getLastNotifiedStatus() { return lastNotifiedStatus; }
        public void setLastNotifiedStatus(String lastNotifiedStatus) { this.lastNotifiedStatus = lastNotifiedStatus; }
        public Instant getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    }

    /** Append-only audit trail: nothing in the application updates or deletes these rows. */
    @Entity(name = "InventoryMovement")
    @Table(name = "inventory_movements")
    public static class Movement {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        @Column(name = "owner_user_id", nullable = false)
        private Long ownerUserId;
        @Column(name = "unit_id")
        private Long unitId;
        @Column(name = "blood_type", nullable = false, length = 3)
        private String bloodType;
        @Enumerated(EnumType.STRING)
        @Column(nullable = false, length = 20)
        private Component component;
        @Column(nullable = false)
        private int delta;
        @Enumerated(EnumType.STRING)
        @Column(nullable = false, length = 20)
        private MovementReason reason;
        @Column(name = "request_id")
        private Long requestId;
        @Column(length = 255)
        private String note;
        @Column(name = "created_by")
        private Long createdBy;
        @Column(name = "created_at", nullable = false, updatable = false)
        private Instant createdAt = Instant.now();

        public Long getId() { return id; }
        public Long getOwnerUserId() { return ownerUserId; }
        public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
        public Long getUnitId() { return unitId; }
        public void setUnitId(Long unitId) { this.unitId = unitId; }
        public String getBloodType() { return bloodType; }
        public void setBloodType(String bloodType) { this.bloodType = bloodType; }
        public Component getComponent() { return component; }
        public void setComponent(Component component) { this.component = component; }
        public int getDelta() { return delta; }
        public void setDelta(int delta) { this.delta = delta; }
        public MovementReason getReason() { return reason; }
        public void setReason(MovementReason reason) { this.reason = reason; }
        public Long getRequestId() { return requestId; }
        public void setRequestId(Long requestId) { this.requestId = requestId; }
        public String getNote() { return note; }
        public void setNote(String note) { this.note = note; }
        public Long getCreatedBy() { return createdBy; }
        public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
        public Instant getCreatedAt() { return createdAt; }
        public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    }

    /** Where an institution is, and whether its stock status may appear on the public ticker. */
    @Entity(name = "InventorySite")
    @Table(name = "inventory_sites")
    public static class Site {
        @Id
        @Column(name = "owner_user_id")
        private Long ownerUserId;
        @Column(name = "site_name", nullable = false, length = 150)
        private String siteName;
        @Column(length = 50)
        private String district;
        private Double latitude;
        private Double longitude;
        @Column(name = "public_signals", nullable = false)
        private boolean publicSignals = true;
        @Column(name = "confirmed_at")
        private Instant confirmedAt;
        @Column(name = "updated_at", nullable = false)
        private Instant updatedAt = Instant.now();

        public Long getOwnerUserId() { return ownerUserId; }
        public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
        public String getSiteName() { return siteName; }
        public void setSiteName(String siteName) { this.siteName = siteName; }
        public String getDistrict() { return district; }
        public void setDistrict(String district) { this.district = district; }
        public Double getLatitude() { return latitude; }
        public void setLatitude(Double latitude) { this.latitude = latitude; }
        public Double getLongitude() { return longitude; }
        public void setLongitude(Double longitude) { this.longitude = longitude; }
        public boolean isPublicSignals() { return publicSignals; }
        public void setPublicSignals(boolean publicSignals) { this.publicSignals = publicSignals; }
        public Instant getConfirmedAt() { return confirmedAt; }
        public void setConfirmedAt(Instant confirmedAt) { this.confirmedAt = confirmedAt; }
        public Instant getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    }
}
