package com.suwasarana.api.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.suwasarana.api.user.Role;
import com.suwasarana.api.user.VerificationStatus;

public class AuthResponse {
    private String accessToken;
    private Long userId;
    private Role role;
    private String email;
    private String phoneNumber;
    private VerificationStatus verificationStatus;

    @JsonIgnore
    private String refreshToken;

    public AuthResponse(String accessToken, Long userId, Role role, String email, String phoneNumber, String refreshToken, VerificationStatus verificationStatus) {
        this.accessToken = accessToken;
        this.userId = userId;
        this.role = role;
        this.email = email;
        this.phoneNumber = phoneNumber;
        this.refreshToken = refreshToken;
        this.verificationStatus = verificationStatus;
    }

    // Getters and Setters
    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
    public VerificationStatus getVerificationStatus() { return verificationStatus; }
    public void setVerificationStatus(VerificationStatus verificationStatus) { this.verificationStatus = verificationStatus; }
}
