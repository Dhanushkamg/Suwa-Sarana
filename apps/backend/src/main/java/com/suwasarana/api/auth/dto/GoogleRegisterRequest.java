package com.suwasarana.api.auth.dto;

import com.suwasarana.api.auth.AuthCodes;
import com.suwasarana.api.user.PhoneNumbers;
import com.suwasarana.api.user.Role;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Second step for a first-time Google user: role, name, phone (proved by OTP) and consent. Messages are error codes. */
public class GoogleRegisterRequest {
    @NotBlank(message = AuthCodes.GOOGLE_INVALID)
    private String idToken;

    @NotBlank(message = AuthCodes.PHONE_REQUIRED)
    @Pattern(regexp = PhoneNumbers.MOBILE_E164_REGEX, message = AuthCodes.PHONE_INVALID)
    private String phoneNumber;

    @NotBlank(message = AuthCodes.FIRST_NAME_REQUIRED)
    @Size(max = 100, message = AuthCodes.NAME_TOO_LONG)
    private String firstName;

    @NotBlank(message = AuthCodes.LAST_NAME_REQUIRED)
    @Size(max = 100, message = AuthCodes.NAME_TOO_LONG)
    private String lastName;

    private String nicNumber;

    /** Kept for old clients; phone ownership is proven by /auth/verify-otp. */
    private String otpCode;

    @NotNull(message = AuthCodes.ROLE_REQUIRED)
    private Role role;

    @NotNull(message = AuthCodes.CONSENT_REQUIRED)
    @AssertTrue(message = AuthCodes.CONSENT_REQUIRED)
    private Boolean acceptTerms;

    @Pattern(regexp = "^(en|si|ta)$", message = AuthCodes.LANGUAGE_INVALID)
    private String preferredLanguage;

    public String getIdToken() { return idToken; }
    public void setIdToken(String idToken) { this.idToken = idToken; }
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = PhoneNumbers.normalizeOrKeep(phoneNumber); }
    public String getNicNumber() { return nicNumber; }
    public void setNicNumber(String nicNumber) { this.nicNumber = nicNumber; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName == null ? null : firstName.trim(); }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName == null ? null : lastName.trim(); }
    public String getOtpCode() { return otpCode; }
    public void setOtpCode(String otpCode) { this.otpCode = otpCode; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public Boolean getAcceptTerms() { return acceptTerms; }
    public void setAcceptTerms(Boolean acceptTerms) { this.acceptTerms = acceptTerms; }
    public String getPreferredLanguage() { return preferredLanguage; }
    public void setPreferredLanguage(String preferredLanguage) { this.preferredLanguage = preferredLanguage; }
}
