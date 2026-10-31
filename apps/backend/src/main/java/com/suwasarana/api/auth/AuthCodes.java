package com.suwasarana.api.auth;

/**
 * Every machine-readable error code the authentication, identity, admin and privacy APIs return.
 * The API sends codes only; the frontend translates them (en/si/ta). Keep ALL of them here.
 *
 * {@link AuthException} (an {@code ApiException}) turns a code into an HTTP error; the English fallback text of each
 * code is in {@code i18n/error_messages_en.properties}. Together with {@code common.ErrorCodes} this is the platform's
 * code list (docs/API-CONTRACT.md).
 */
public final class AuthCodes {

    private AuthCodes() {
    }

    // ---- generic -------------------------------------------------------------------------------------------
    public static final String VALIDATION_FAILED = com.suwasarana.api.common.ErrorCodes.VALIDATION_FAILED;
    public static final String NOT_FOUND = "common.notFound";

    // ---- success messages (also codes: the frontend translates) --------------------------------------------
    public static final String OK = "common.ok";
    public static final String OTP_SENT = "auth.otp.sent";
    public static final String OTP_VERIFIED = "auth.otp.verified";
    public static final String PASSWORD_UPDATED = "auth.password.updated";
    public static final String RESET_EMAIL_REQUESTED = "auth.reset.emailRequested";
    public static final String EMAIL_VERIFIED = "auth.emailVerification.verified";
    public static final String EMAIL_VERIFICATION_SENT = "auth.emailVerification.sent";
    public static final String LOGGED_OUT = "auth.logout.ok";

    // ---- field-level validation (Bean Validation messages are these codes) ----------------------------------
    public static final String EMAIL_REQUIRED = "auth.email.required";
    public static final String EMAIL_INVALID = "auth.email.invalid";
    public static final String PHONE_REQUIRED = "auth.phone.required";
    public static final String PHONE_INVALID = "auth.phone.invalid";
    public static final String PHONE_IN_USE = "auth.phone.inUse";
    public static final String PHONE_NOT_VERIFIED = "auth.phone.notVerified";
    public static final String PASSWORD_REQUIRED = "auth.password.required";
    public static final String PASSWORD_TOO_SHORT = "auth.password.tooShort";
    public static final String PASSWORD_TOO_LONG = "auth.password.tooLong";
    public static final String PASSWORD_COMMON = "auth.password.common";
    public static final String PASSWORD_SAME_AS_IDENTIFIER = "auth.password.sameAsIdentifier";
    public static final String PASSWORD_WRONG = "auth.password.wrong";
    public static final String FIRST_NAME_REQUIRED = "auth.firstName.required";
    public static final String LAST_NAME_REQUIRED = "auth.lastName.required";
    public static final String NAME_TOO_LONG = "auth.name.tooLong";
    public static final String ROLE_REQUIRED = "auth.role.required";
    public static final String ROLE_NOT_ALLOWED = "auth.role.notAllowed";
    public static final String NIC_INVALID = "auth.nic.invalid";
    public static final String NIC_IN_USE = "auth.nic.inUse";
    public static final String CONSENT_REQUIRED = "auth.consent.required";
    public static final String LANGUAGE_INVALID = "user.language.invalid";
    public static final String OTP_REQUIRED = "auth.otp.required";
    public static final String TOKEN_REQUIRED = "auth.token.required";
    public static final String REASON_REQUIRED = "admin.reason.required";
    public static final String REASON_TOO_LONG = "admin.reason.tooLong";

    // ---- registration / login ------------------------------------------------------------------------------
    /** Same code for "email taken" and "phone taken" on purpose: registration must not reveal which one exists (F-37). */
    public static final String ACCOUNT_EXISTS = "auth.register.duplicate";
    public static final String CREDENTIALS_INVALID = "auth.credentials.invalid";
    public static final String ACCOUNT_LOCKED = "auth.account.locked";
    public static final String ACCOUNT_SUSPENDED = "auth.account.suspended";
    public static final String ACCOUNT_DELETED = "auth.account.deleted";

    // ---- OTP / SMS -----------------------------------------------------------------------------------------
    public static final String OTP_INVALID = "auth.otp.invalid";
    public static final String OTP_COOLDOWN = "auth.otp.cooldown";
    public static final String SMS_FAILED = "auth.sms.failed";

    // ---- sessions ------------------------------------------------------------------------------------------
    public static final String SESSION_EXPIRED = "auth.session.expired";
    public static final String NOT_SIGNED_IN = "auth.session.notSignedIn";
    public static final String ORIGIN_BLOCKED = "auth.origin.blocked";

    // ---- email verification / password reset ----------------------------------------------------------------
    public static final String EMAIL_VERIFY_TOKEN_INVALID = "auth.emailVerification.invalid";
    public static final String EMAIL_VERIFY_COOLDOWN = "auth.emailVerification.cooldown";
    public static final String EMAIL_ALREADY_VERIFIED = "auth.emailVerification.alreadyVerified";
    public static final String EMAIL_NOT_VERIFIED = "email.verificationRequired";
    public static final String RESET_TOKEN_INVALID = "auth.reset.tokenInvalid";
    public static final String RESET_NOT_ALLOWED = "auth.reset.notAllowed";

    // ---- Google --------------------------------------------------------------------------------------------
    public static final String GOOGLE_INVALID = "auth.google.invalid";
    public static final String GOOGLE_UNAVAILABLE = "auth.google.unavailable";
    public static final String GOOGLE_LINK_PASSWORD_REQUIRED = "auth.google.linkNeedsPassword";
    public static final String GOOGLE_ACCOUNT_MISMATCH = "auth.google.accountMismatch";

    // ---- verification guard (used by other modules) ----------------------------------------------------------
    public static final String VERIFICATION_REQUIRED = "verification.required";
    public static final String VERIFICATION_REJECTED = "verification.rejected";

    // ---- admin ---------------------------------------------------------------------------------------------
    public static final String ADMIN_TARGET_PROTECTED = "admin.target.protected";
    public static final String ADMIN_TARGET_SELF = "admin.target.self";
    public static final String ADMIN_NOT_VERIFIABLE = "admin.verification.notApplicable";
    public static final String ADMIN_ALREADY_DECIDED = "admin.verification.alreadyDecided";
    public static final String ADMIN_STATUS_UNCHANGED = "admin.status.unchanged";
    public static final String ADMIN_ROLE_INVALID = "admin.role.invalid";
    public static final String INVITE_INVALID = "auth.invite.invalid";
    public static final String INVITE_EXISTS = "admin.invite.userExists";

    // ---- privacy -------------------------------------------------------------------------------------------
    public static final String DELETE_CONFIRMATION_REQUIRED = "privacy.delete.confirmationRequired";
    public static final String DELETE_CONFIRMATION_INVALID = "privacy.delete.confirmationInvalid";
    public static final String DELETE_ADMIN_FORBIDDEN = "privacy.delete.adminForbidden";
    public static final String PHONE_UNCHANGED = "user.phone.unchanged";
}
