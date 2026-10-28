package com.suwasarana.api.donor;

/** How a donation came to be recorded. */
public enum DonationSource {
    /** The requester or hospital confirmed an accepted match as donated. */
    MATCH,
    /** Camp/hospital staff checked the donor in at a booked slot. */
    SLOT_CHECKIN,
    /** Reserved for staff-entered history (no endpoint yet). */
    MANUAL
}
