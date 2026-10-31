package com.suwasarana.api.common;

/**
 * Stable, machine-readable error codes returned in {@code ApiResponse.code}. The frontend maps a code to a localised
 * message (en/si/ta); {@code message} stays an English fallback for logs and developers. Codes are
 * {@code <area>.<reason>} in lower snake case and must never be renamed once released.
 *
 * <p>THE single code list of the platform, together with {@code auth.AuthCodes} (identity / admin / privacy codes,
 * same format). See docs/API-CONTRACT.md for the full table.</p>
 *
 * <p>Services may pass any other well-formed code via {@code new ApiException(status, "area.reason", "message")};
 * this list only covers the codes the platform layer emits itself.</p>
 */
public final class ErrorCodes {

    private ErrorCodes() {}

    public static final String VALIDATION_FAILED = "validation.failed";
    public static final String REQUEST_MALFORMED = "request.malformed";
    public static final String AUTH_UNAUTHORIZED = "auth.unauthorized";
    public static final String AUTH_INVALID_CREDENTIALS = "auth.invalid_credentials";
    public static final String AUTH_FORBIDDEN = "auth.forbidden";
    public static final String REQUESTER_NOT_VERIFIED = "request.requester_not_verified";
    public static final String CIRCLE_NOT_FOUND = "circle.not_found";
    public static final String CIRCLE_EXPIRED = "circle.expired";
    public static final String SLOT_NOT_FOUND = "slot.not_found";
    public static final String SLOT_FULL = "slot.full";
    public static final String SLOT_DUPLICATE_BOOKING = "slot.duplicate_booking";
    public static final String DONOR_INELIGIBLE = "donor.ineligible";
    public static final String DATA_CONFLICT = "data.conflict";
    public static final String RATE_LIMITED = "rate.limited";
    public static final String INTERNAL_ERROR = "internal.error";

    // ---- requests -------------------------------------------------------------------------------------------
    public static final String REQUEST_NOT_FOUND = "request.not_found";
    public static final String REQUEST_NOT_OWNED = "request.not_owned";
    public static final String REQUEST_NOT_ACTIVE = "request.not_active";
    public static final String REQUEST_ALREADY_CLOSED = "request.already_closed";
    public static final String REQUEST_DUPLICATE = "request.duplicate";
    public static final String REQUEST_LIMIT_REACHED = "request.limit_reached";
    public static final String REPORT_ALREADY_FILED = "request.report_already_filed";
    public static final String CANNOT_REPORT_OWN_REQUEST = "request.cannot_report_own";
    public static final String UNKNOWN_DISTRICT = "geo.unknown_district";
    public static final String OUTSIDE_SRI_LANKA = "geo.outside_sri_lanka";

    // ---- matches and donations ----------------------------------------------------------------------------------
    public static final String MATCH_NOT_FOUND = "match.not_found";
    public static final String MATCH_ALREADY_ANSWERED = "match.already_answered";
    public static final String MATCH_NOT_ACCEPTED = "match.not_accepted";
    public static final String DONATION_ALREADY_RECORDED = "donation.already_recorded";
    public static final String DONOR_PROFILE_NOT_FOUND = "donor.profile_not_found";

    // ---- camps and slots ------------------------------------------------------------------------------------------
    public static final String CAMP_NOT_FOUND = "camp.not_found";
    public static final String CAMP_IN_PAST = "camp.in_past";
    public static final String TIME_RANGE_INVALID = "time.range_invalid";
    public static final String SLOT_IN_PAST = "slot.in_past";
    public static final String SLOT_NOT_BOOKABLE = "slot.not_bookable";
    public static final String BOOKING_OVERLAP = "slot.booking_overlap";
    public static final String BLOOD_GROUP_INVALID = "blood.group_invalid";

    // ---- inventory --------------------------------------------------------------------------------------------------
    public static final String STOCK_NOT_FOUND = "inventory.stock_not_found";
    public static final String INSUFFICIENT_STOCK = "inventory.insufficient_stock";
    public static final String THRESHOLD_INVALID = "inventory.threshold_invalid";
    public static final String STOCK_DATE_INVALID = "inventory.date_invalid";

    // ---- notifications ----------------------------------------------------------------------------------------------
    public static final String NOTIFICATION_NOT_FOUND = "notification.not_found";

    /** Fallback for framework-generated HTTP errors: {@code http.not_found}, {@code http.method_not_allowed}, ... */
    public static String forHttpStatus(int status) {
        return switch (status) {
            case 400 -> REQUEST_MALFORMED;
            case 401 -> AUTH_UNAUTHORIZED;
            case 403 -> AUTH_FORBIDDEN;
            case 404 -> "http.not_found";
            case 405 -> "http.method_not_allowed";
            case 406 -> "http.not_acceptable";
            case 409 -> DATA_CONFLICT;
            case 413 -> "http.payload_too_large";
            case 415 -> "http.unsupported_media_type";
            case 429 -> RATE_LIMITED;
            default -> status >= 500 ? INTERNAL_ERROR : "http.error";
        };
    }
}
