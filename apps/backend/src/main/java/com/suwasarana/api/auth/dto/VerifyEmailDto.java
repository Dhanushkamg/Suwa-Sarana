package com.suwasarana.api.auth.dto;

import com.suwasarana.api.auth.AuthCodes;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class VerifyEmailDto {

    @NotBlank(message = AuthCodes.TOKEN_REQUIRED)
    @Size(max = 200, message = AuthCodes.EMAIL_VERIFY_TOKEN_INVALID)
    private String token;

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
}
