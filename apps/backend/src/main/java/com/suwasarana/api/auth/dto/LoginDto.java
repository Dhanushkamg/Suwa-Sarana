package com.suwasarana.api.auth.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.suwasarana.api.auth.AuthCodes;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class LoginDto {

    /** Email or phone number. */
    @NotBlank(message = AuthCodes.EMAIL_REQUIRED)
    @Size(max = 254, message = AuthCodes.EMAIL_INVALID)
    @JsonAlias({"email", "phoneNumber", "phone"})
    private String username;

    @NotBlank(message = AuthCodes.PASSWORD_REQUIRED)
    @Size(max = 1024, message = AuthCodes.PASSWORD_TOO_LONG)
    private String password;

    // Getters and Setters
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return username;
    }

    public void setEmail(String email) {
        if (this.username == null || this.username.isBlank()) {
            this.username = email;
        }
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
