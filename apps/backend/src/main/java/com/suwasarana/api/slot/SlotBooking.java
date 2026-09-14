package com.suwasarana.api.slot;

import com.suwasarana.api.donor.DonorProfile;
import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Records a donor's booking of a specific DonationSlot.
 * The UNIQUE(slot_id, donor_id) constraint is enforced both at DB level (V14)
 * and in SlotBookingService to provide a friendly error before the DB rejects it.
 */
@Entity
@Table(name = "slot_bookings",
       uniqueConstraints = @UniqueConstraint(columnNames = {"slot_id", "donor_id"}))
public class SlotBooking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "slot_id", nullable = false)
    private DonationSlot slot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "donor_id", nullable = false)
    private DonorProfile donor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status = BookingStatus.BOOKED;

    @Column(name = "booked_at", nullable = false, updatable = false)
    private Instant bookedAt = Instant.now();

    @Column(name = "checked_in_at")
    private Instant checkedInAt;

    /** Date on which the donor was last confirmed NBTS-eligible at booking time. */
    @Column(name = "eligible_as_of")
    private LocalDate eligibleAsOf;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public DonationSlot getSlot() { return slot; }
    public void setSlot(DonationSlot slot) { this.slot = slot; }

    public DonorProfile getDonor() { return donor; }
    public void setDonor(DonorProfile donor) { this.donor = donor; }

    public BookingStatus getStatus() { return status; }
    public void setStatus(BookingStatus status) { this.status = status; }

    public Instant getBookedAt() { return bookedAt; }
    public void setBookedAt(Instant bookedAt) { this.bookedAt = bookedAt; }

    public Instant getCheckedInAt() { return checkedInAt; }
    public void setCheckedInAt(Instant checkedInAt) { this.checkedInAt = checkedInAt; }

    public LocalDate getEligibleAsOf() { return eligibleAsOf; }
    public void setEligibleAsOf(LocalDate eligibleAsOf) { this.eligibleAsOf = eligibleAsOf; }
}
