package com.suwasarana.api.slot;

import com.suwasarana.api.camp.DonationCamp;
import com.suwasarana.api.user.User;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;

/**
 * Represents a timed donation window belonging to either a DonationCamp or a
 * hospital user (User with role HOSPITAL_REQUESTER). Exactly one of campId /
 * hospitalUser must be non-null — enforced by the DB CHECK constraint in V14.
 */
@Entity
@Table(name = "donation_slots")
public class DonationSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "camp_id")
    private DonationCamp camp;

    /**
     * Hospital owner. References users(id) where role = HOSPITAL_REQUESTER.
     * No dedicated hospitals table exists in the schema.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hospital_id")
    private User hospitalUser;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Column(nullable = false)
    private Integer capacity;

    @Column(name = "booked_count", nullable = false)
    private Integer bookedCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SlotStatus status = SlotStatus.OPEN;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt = ZonedDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt = ZonedDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        updatedAt = ZonedDateTime.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public DonationCamp getCamp() { return camp; }
    public void setCamp(DonationCamp camp) { this.camp = camp; }

    public User getHospitalUser() { return hospitalUser; }
    public void setHospitalUser(User hospitalUser) { this.hospitalUser = hospitalUser; }

    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }

    public Integer getCapacity() { return capacity; }
    public void setCapacity(Integer capacity) { this.capacity = capacity; }

    public Integer getBookedCount() { return bookedCount; }
    public void setBookedCount(Integer bookedCount) { this.bookedCount = bookedCount; }

    public SlotStatus getStatus() { return status; }
    public void setStatus(SlotStatus status) { this.status = status; }

    public ZonedDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(ZonedDateTime createdAt) { this.createdAt = createdAt; }

    public ZonedDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(ZonedDateTime updatedAt) { this.updatedAt = updatedAt; }

    /** Convenience: returns the display name of whichever host owns this slot. */
    public String resolveHostName() {
        if (camp != null) return camp.getName();
        if (hospitalUser != null) return hospitalUser.getEmail();
        return "Unknown";
    }
}
