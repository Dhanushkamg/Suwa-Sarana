package com.suwasarana.api.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.suwasarana.api.user.Role;
import com.suwasarana.api.user.User;
import com.suwasarana.api.user.VerificationStatus;

/** Session returned by login / register / refresh. The refresh token travels only in the HttpOnly cookie. */
public class AuthResponse {
    private String accessToken;
    private Long userId;
    private Role role;
    private String email;
    private String phoneNumber;
    private VerificationStatus verificationStatus;
    private String firstName;
    private String lastName;
    private boolean emailVerified;
    private String preferredLanguage;

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

    public static AuthResponse of(User user, String accessToken, String refreshToken) {
        AuthResponse response = new AuthResponse(accessToken, user.getId(), user.getRole(), user.getEmail(),
                user.getPhoneNumber(), refreshToken, user.getVerificationStatus());
        response.firstName = user.getFirstName();
        response.lastName = user.getLastName();
        response.emailVerified = user.isEmailVerified();
        response.preferredLanguage = user.getPreferredLanguage();
        return response;
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
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public boolean isEmailVerified() { return emailVerified; }
    public void setEmailVerified(boolean emailVerified) { this.emailVerified = emailVerified; }
    public String getPreferredLanguage() { return preferredLanguage; }
    public void setPreferredLanguage(String preferredLanguage) { this.preferredLanguage = preferredLanguage; }
}
