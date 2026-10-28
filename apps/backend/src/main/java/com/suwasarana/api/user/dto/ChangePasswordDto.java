package com.suwasarana.api.user.dto;

import com.suwasarana.api.auth.AuthCodes;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ChangePasswordDto {

    @NotBlank(message = AuthCodes.PASSWORD_REQUIRED)
    @Size(max = 1024, message = AuthCodes.PASSWORD_TOO_LONG)
    private String currentPassword;

    @NotBlank(message = AuthCodes.PASSWORD_REQUIRED)
    @Size.List({
            @Size(min = 10, message = AuthCodes.PASSWORD_TOO_SHORT),
            @Size(max = 128, message = AuthCodes.PASSWORD_TOO_LONG)
    })
    private String newPassword;

    public String getCurrentPassword() { return currentPassword; }
    public void setCurrentPassword(String currentPassword) { this.currentPassword = currentPassword; }
    public String getNewPassword() { return newPassword; }
    public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
}
