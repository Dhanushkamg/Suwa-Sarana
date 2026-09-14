package com.suwasarana.api.slot.dto;

import com.suwasarana.api.slot.BookingStatus;

import java.time.Instant;

public class SlotBookingDto {

    private Long id;
    private Long slotId;
    private Long donorId;
    private BookingStatus status;
    private Instant bookedAt;
    private Instant checkedInAt;
    private DonationSlotDto slot;   // embedded slot summary for MyBookings view

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getSlotId() { return slotId; }
    public void setSlotId(Long slotId) { this.slotId = slotId; }

    public Long getDonorId() { return donorId; }
    public void setDonorId(Long donorId) { this.donorId = donorId; }

    public BookingStatus getStatus() { return status; }
    public void setStatus(BookingStatus status) { this.status = status; }

    public Instant getBookedAt() { return bookedAt; }
    public void setBookedAt(Instant bookedAt) { this.bookedAt = bookedAt; }

    public Instant getCheckedInAt() { return checkedInAt; }
    public void setCheckedInAt(Instant checkedInAt) { this.checkedInAt = checkedInAt; }

    public DonationSlotDto getSlot() { return slot; }
    public void setSlot(DonationSlotDto slot) { this.slot = slot; }
}
