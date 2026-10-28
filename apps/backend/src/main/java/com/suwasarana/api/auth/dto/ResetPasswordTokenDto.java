package com.suwasarana.api.auth.dto;

import com.suwasarana.api.auth.AuthCodes;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Password reset with the single-use token from the reset email. */
public class ResetPasswordTokenDto {

    @NotBlank(message = AuthCodes.TOKEN_REQUIRED)
    @Size(max = 200, message = AuthCodes.RESET_TOKEN_INVALID)
    private String token;

    @NotBlank(message = AuthCodes.PASSWORD_REQUIRED)
    @Size.List({
            @Size(min = 10, message = AuthCodes.PASSWORD_TOO_SHORT),
            @Size(max = 128, message = AuthCodes.PASSWORD_TOO_LONG)
    })
    private String newPassword;

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getNewPassword() { return newPassword; }
    public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
}
