package com.suwasarana.api.common;

/**
 * Stable, machine-readable error codes returned in {@code ApiResponse.code}. The frontend maps a code to a localised
 * message (en/si/ta); {@code message} stays an English fallback for logs and developers. Codes are
 * {@code <area>.<reason>} in lower snake case and must never be renamed once released.
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
    public static final String RATE_LIMITED = "rate_limit.exceeded";
    public static final String INTERNAL_ERROR = "internal.error";

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
