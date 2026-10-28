package com.suwasarana.api.auth.dto;

import com.suwasarana.api.auth.AuthCodes;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Locale;

public class ForgotPasswordDto {

    @NotBlank(message = AuthCodes.EMAIL_REQUIRED)
    @Email(message = AuthCodes.EMAIL_INVALID)
    @Size(max = 254, message = AuthCodes.EMAIL_INVALID)
    private String email;

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email == null ? null : email.trim().toLowerCase(Locale.ROOT); }
}
