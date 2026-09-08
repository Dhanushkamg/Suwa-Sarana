package com.suwasarana.api.auth.dto;

import com.suwasarana.api.user.Role;

public class AuthResponse {
    private String accessToken;
    private Long userId;
    private Role role;

    public AuthResponse(String accessToken, Long userId, Role role) {
        this.accessToken = accessToken;
        this.userId = userId;
        this.role = role;
    }

    // Getters and Setters
    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
}
