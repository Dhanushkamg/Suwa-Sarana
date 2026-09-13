package com.suwasarana.api.auth.dto;

import com.suwasarana.api.user.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public class GoogleRegisterRequest {
    @NotBlank(message = "ID Token is required")
    private String idToken;

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^\\+94[0-9]{9}$", message = "Phone number must be in format +94XXXXXXXXX")
    private String phoneNumber;

    private String nicNumber;

    @NotNull(message = "Role is required")
    private Role role;

    public String getIdToken() {
        return idToken;
    }

    public void setIdToken(String idToken) {
        this.idToken = idToken;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getNicNumber() {
        return nicNumber;
    }

    public void setNicNumber(String nicNumber) {
        this.nicNumber = nicNumber;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }
}
