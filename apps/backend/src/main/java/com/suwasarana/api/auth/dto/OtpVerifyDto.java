package com.suwasarana.api.auth.dto;

import com.suwasarana.api.auth.AuthCodes;
import com.suwasarana.api.user.PhoneNumbers;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class OtpVerifyDto {

    @NotBlank(message = AuthCodes.PHONE_REQUIRED)
    @Pattern(regexp = PhoneNumbers.MOBILE_E164_REGEX, message = AuthCodes.PHONE_INVALID)
    private String phoneNumber;

    @NotBlank(message = AuthCodes.OTP_REQUIRED)
    @Size(max = 12, message = AuthCodes.OTP_INVALID)
    private String otpCode;

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = PhoneNumbers.normalizeOrKeep(phoneNumber);
    }

    public String getOtpCode() {
        return otpCode;
    }

    public void setOtpCode(String otpCode) {
        this.otpCode = otpCode;
    }
}
