package com.suwasarana.api.user.dto;

import jakarta.validation.constraints.Size;

/** Confirmation for erasing the account: the password, or a code sent by {@code POST /api/users/me/send-otp}. One of the two. */
public class DeleteAccountDto {

    @Size(max = 1024)
    private String password;

    @Size(max = 12)
    private String otpCode;

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getOtpCode() { return otpCode; }
    public void setOtpCode(String otpCode) { this.otpCode = otpCode; }
}
