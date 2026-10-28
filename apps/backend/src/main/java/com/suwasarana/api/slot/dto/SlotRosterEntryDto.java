package com.suwasarana.api.slot.dto;

import com.suwasarana.api.slot.BookingStatus;
import com.suwasarana.api.slot.SlotBooking;
import com.suwasarana.api.user.User;

import java.time.Instant;

/** One booked donor as the slot organiser sees them: enough to check them in, nothing more. */
public class SlotRosterEntryDto {

    private Long bookingId;
    private Long donorId;
    private String donorName;
    private String bloodType;
    private BookingStatus status;
    private Instant bookedAt;
    private Instant checkedInAt;

    public static SlotRosterEntryDto from(SlotBooking b) {
        SlotRosterEntryDto d = new SlotRosterEntryDto();
        d.bookingId = b.getId();
        d.donorId = b.getDonor().getId();
        d.donorName = shortName(b.getDonor().getUser());
        d.bloodType = b.getDonor().getBloodType();
        d.status = b.getStatus();
        d.bookedAt = b.getBookedAt();
        d.checkedInAt = b.getCheckedInAt();
        return d;
    }

    /** "Kasun P." */
    private static String shortName(User u) {
        if (u == null) return "";
        String first = u.getFirstName() == null ? "" : u.getFirstName().trim();
        String last = u.getLastName() == null ? "" : u.getLastName().trim();
        return last.isEmpty() ? first : first + " " + last.charAt(0) + ".";
    }

    public Long getBookingId() { return bookingId; }
    public Long getDonorId() { return donorId; }
    public String getDonorName() { return donorName; }
    public String getBloodType() { return bloodType; }
    public BookingStatus getStatus() { return status; }
    public Instant getBookedAt() { return bookedAt; }
    public Instant getCheckedInAt() { return checkedInAt; }
}
