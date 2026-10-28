package com.suwasarana.api.auth.dto;

import com.suwasarana.api.auth.AuthCodes;
import com.suwasarana.api.user.PhoneNumbers;
import com.suwasarana.api.user.Role;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Locale;

/**
 * Registration with email + password. Every message below is an error CODE (the frontend translates it);
 * the validation response is {@code data: {field: code}}. Email is lower-cased and the phone normalised to
 * E.164 on the way in, so the rest of the system only ever sees one spelling.
 */
public class RegisterDto {

    @NotBlank(message = AuthCodes.EMAIL_REQUIRED)
    @Email(message = AuthCodes.EMAIL_INVALID)
    @Size(max = 254, message = AuthCodes.EMAIL_INVALID)
    private String email;

    @NotBlank(message = AuthCodes.PHONE_REQUIRED)
    @Pattern(regexp = PhoneNumbers.MOBILE_E164_REGEX, message = AuthCodes.PHONE_INVALID)
    private String phoneNumber;

    @NotBlank(message = AuthCodes.PASSWORD_REQUIRED)
    @Size.List({
            @Size(min = 10, message = AuthCodes.PASSWORD_TOO_SHORT),
            @Size(max = 128, message = AuthCodes.PASSWORD_TOO_LONG)
    })
    private String password;

    @NotBlank(message = AuthCodes.FIRST_NAME_REQUIRED)
    @Size(max = 100, message = AuthCodes.NAME_TOO_LONG)
    private String firstName;

    @NotBlank(message = AuthCodes.LAST_NAME_REQUIRED)
    @Size(max = 100, message = AuthCodes.NAME_TOO_LONG)
    private String lastName;

    @NotNull(message = AuthCodes.ROLE_REQUIRED)
    private Role role;

    /** Optional. Blank is treated as "not given". Stored only as a keyed hash. */
    private String nicNumber;

    /** Kept for old clients; phone ownership is proven by /auth/verify-otp, not by this field. */
    private String otpCode;

    /** Consent to the terms and the privacy notice; the server records the version and time. */
    @NotNull(message = AuthCodes.CONSENT_REQUIRED)
    @AssertTrue(message = AuthCodes.CONSENT_REQUIRED)
    private Boolean acceptTerms;

    /** en | si | ta; defaults to the Accept-Language header, then en. */
    @Pattern(regexp = "^(en|si|ta)$", message = AuthCodes.LANGUAGE_INVALID)
    private String preferredLanguage;

    // Getters and Setters
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email == null ? null : email.trim().toLowerCase(Locale.ROOT); }
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = PhoneNumbers.normalizeOrKeep(phoneNumber); }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName == null ? null : firstName.trim(); }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName == null ? null : lastName.trim(); }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public String getNicNumber() { return nicNumber; }
    public void setNicNumber(String nicNumber) { this.nicNumber = nicNumber; }
    public String getOtpCode() { return otpCode; }
    public void setOtpCode(String otpCode) { this.otpCode = otpCode; }
    public Boolean getAcceptTerms() { return acceptTerms; }
    public void setAcceptTerms(Boolean acceptTerms) { this.acceptTerms = acceptTerms; }
    public String getPreferredLanguage() { return preferredLanguage; }
    public void setPreferredLanguage(String preferredLanguage) { this.preferredLanguage = preferredLanguage; }
}
