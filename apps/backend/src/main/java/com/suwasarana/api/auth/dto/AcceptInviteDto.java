package com.suwasarana.api.auth.dto;

import com.suwasarana.api.auth.AuthCodes;
import com.suwasarana.api.user.PhoneNumbers;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Creates an ADMIN account from an invitation. The email comes from the invitation, not from the client. */
public class AcceptInviteDto {

    @NotBlank(message = AuthCodes.TOKEN_REQUIRED)
    @Size(max = 200, message = AuthCodes.INVITE_INVALID)
    private String token;

    @NotBlank(message = AuthCodes.FIRST_NAME_REQUIRED)
    @Size(max = 100, message = AuthCodes.NAME_TOO_LONG)
    private String firstName;

    @NotBlank(message = AuthCodes.LAST_NAME_REQUIRED)
    @Size(max = 100, message = AuthCodes.NAME_TOO_LONG)
    private String lastName;

    /** Must have been verified with /auth/send-otp + /auth/verify-otp first. */
    @NotBlank(message = AuthCodes.PHONE_REQUIRED)
    @Pattern(regexp = PhoneNumbers.MOBILE_E164_REGEX, message = AuthCodes.PHONE_INVALID)
    private String phoneNumber;

    @NotBlank(message = AuthCodes.PASSWORD_REQUIRED)
    @Size.List({
            @Size(min = 10, message = AuthCodes.PASSWORD_TOO_SHORT),
            @Size(max = 128, message = AuthCodes.PASSWORD_TOO_LONG)
    })
    private String password;

    @NotNull(message = AuthCodes.CONSENT_REQUIRED)
    @AssertTrue(message = AuthCodes.CONSENT_REQUIRED)
    private Boolean acceptTerms;

    @Pattern(regexp = "^(en|si|ta)$", message = AuthCodes.LANGUAGE_INVALID)
    private String preferredLanguage;

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName == null ? null : firstName.trim(); }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName == null ? null : lastName.trim(); }
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = PhoneNumbers.normalizeOrKeep(phoneNumber); }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public Boolean getAcceptTerms() { return acceptTerms; }
    public void setAcceptTerms(Boolean acceptTerms) { this.acceptTerms = acceptTerms; }
    public String getPreferredLanguage() { return preferredLanguage; }
    public void setPreferredLanguage(String preferredLanguage) { this.preferredLanguage = preferredLanguage; }
}
