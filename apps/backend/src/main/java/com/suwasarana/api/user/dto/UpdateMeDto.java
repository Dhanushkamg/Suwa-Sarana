package com.suwasarana.api.user.dto;

import com.suwasarana.api.auth.AuthCodes;
import com.suwasarana.api.user.PhoneNumbers;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Partial update of the own profile: send only the fields to change.
 * Changing {@code phoneNumber} requires that the NEW number was first verified with /api/auth/send-otp + /api/auth/verify-otp.
 */
public class UpdateMeDto {

    @Size(max = 100, message = AuthCodes.NAME_TOO_LONG)
    @Pattern(regexp = ".*\\S.*", message = AuthCodes.FIRST_NAME_REQUIRED)
    private String firstName;

    @Size(max = 100, message = AuthCodes.NAME_TOO_LONG)
    @Pattern(regexp = ".*\\S.*", message = AuthCodes.LAST_NAME_REQUIRED)
    private String lastName;

    @Pattern(regexp = "^(en|si|ta)$", message = AuthCodes.LANGUAGE_INVALID)
    private String preferredLanguage;

    @Pattern(regexp = PhoneNumbers.MOBILE_E164_REGEX, message = AuthCodes.PHONE_INVALID)
    private String phoneNumber;

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName == null ? null : firstName.trim(); }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName == null ? null : lastName.trim(); }
    public String getPreferredLanguage() { return preferredLanguage; }
    public void setPreferredLanguage(String preferredLanguage) { this.preferredLanguage = preferredLanguage; }
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber == null ? null : PhoneNumbers.normalizeOrKeep(phoneNumber); }
}
