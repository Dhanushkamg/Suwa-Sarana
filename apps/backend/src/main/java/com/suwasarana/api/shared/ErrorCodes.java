package com.suwasarana.api.shared;

/**
 * Machine-readable error codes for the request lifecycle, matching, camps/slots, inventory and exports.
 * The frontend maps a code to a localised message. Kept in one class so they can be moved onto the platform-wide
 * error envelope in one step.
 */
public final class ErrorCodes {

    private ErrorCodes() {}

    // Requests
    public static final String REQUEST_NOT_FOUND = "REQUEST_NOT_FOUND";
    public static final String REQUEST_NOT_OWNED = "REQUEST_NOT_OWNED";
    public static final String REQUEST_NOT_ACTIVE = "REQUEST_NOT_ACTIVE";
    public static final String REQUEST_ALREADY_CLOSED = "REQUEST_ALREADY_CLOSED";
    public static final String REQUEST_DUPLICATE = "REQUEST_DUPLICATE";
    public static final String REQUEST_LIMIT_REACHED = "REQUEST_LIMIT_REACHED";
    public static final String REQUESTER_NOT_ALLOWED = "REQUESTER_NOT_ALLOWED";
    public static final String UNKNOWN_DISTRICT = "UNKNOWN_DISTRICT";
    public static final String OUTSIDE_SRI_LANKA = "OUTSIDE_SRI_LANKA";
    public static final String REPORT_ALREADY_FILED = "REPORT_ALREADY_FILED";
    public static final String CANNOT_REPORT_OWN_REQUEST = "CANNOT_REPORT_OWN_REQUEST";

    // Matches and donations
    public static final String MATCH_NOT_FOUND = "MATCH_NOT_FOUND";
    public static final String MATCH_ALREADY_ANSWERED = "MATCH_ALREADY_ANSWERED";
    public static final String MATCH_NOT_ACCEPTED = "MATCH_NOT_ACCEPTED";
    public static final String DONATION_ALREADY_RECORDED = "DONATION_ALREADY_RECORDED";

    // Camps and slots
    public static final String CAMP_NOT_FOUND = "CAMP_NOT_FOUND";
    public static final String CAMP_IN_PAST = "CAMP_IN_PAST";
    public static final String TIME_RANGE_INVALID = "TIME_RANGE_INVALID";
    public static final String SLOT_IN_PAST = "SLOT_IN_PAST";
    public static final String SLOT_NOT_BOOKABLE = "SLOT_NOT_BOOKABLE";
    public static final String BOOKING_OVERLAP = "BOOKING_OVERLAP";
    public static final String BLOOD_GROUP_INVALID = "BLOOD_GROUP_INVALID";

    // Inventory
    public static final String INSTITUTION_NOT_VERIFIED = "INSTITUTION_NOT_VERIFIED";
    public static final String STOCK_NOT_FOUND = "STOCK_NOT_FOUND";
    public static final String INSUFFICIENT_STOCK = "INSUFFICIENT_STOCK";
    public static final String THRESHOLD_INVALID = "THRESHOLD_INVALID";
    public static final String STOCK_DATE_INVALID = "STOCK_DATE_INVALID";

    // Notifications
    public static final String NOTIFICATION_NOT_FOUND = "NOTIFICATION_NOT_FOUND";

    // Donor profile
    public static final String DONOR_PROFILE_NOT_FOUND = "DONOR_PROFILE_NOT_FOUND";
}
