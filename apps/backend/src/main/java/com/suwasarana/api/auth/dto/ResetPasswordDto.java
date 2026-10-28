package com.suwasarana.api.auth.dto;

import com.suwasarana.api.auth.AuthCodes;
import com.suwasarana.api.user.PhoneNumbers;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Password reset by phone: the phone must already have been verified through /send-otp + /verify-otp. */
public class ResetPasswordDto {

    @NotBlank(message = AuthCodes.PHONE_REQUIRED)
    @Pattern(regexp = PhoneNumbers.MOBILE_E164_REGEX, message = AuthCodes.PHONE_INVALID)
    private String phoneNumber;

    @NotBlank(message = AuthCodes.PASSWORD_REQUIRED)
    @Size.List({
            @Size(min = 10, message = AuthCodes.PASSWORD_TOO_SHORT),
            @Size(max = 128, message = AuthCodes.PASSWORD_TOO_LONG)
    })
    private String newPassword;

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = PhoneNumbers.normalizeOrKeep(phoneNumber); }
    public String getNewPassword() { return newPassword; }
    public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
}
