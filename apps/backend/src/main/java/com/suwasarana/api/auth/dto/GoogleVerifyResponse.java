package com.suwasarana.api.auth.dto;

public class GoogleVerifyResponse {
    private boolean requiresRegistration;
    private String email;
    private AuthResponse authResponse;

    public GoogleVerifyResponse(boolean requiresRegistration, String email, AuthResponse authResponse) {
        this.requiresRegistration = requiresRegistration;
        this.email = email;
        this.authResponse = authResponse;
    }

    public boolean isRequiresRegistration() {
        return requiresRegistration;
    }

    public void setRequiresRegistration(boolean requiresRegistration) {
        this.requiresRegistration = requiresRegistration;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public AuthResponse getAuthResponse() {
        return authResponse;
    }

    public void setAuthResponse(AuthResponse authResponse) {
        this.authResponse = authResponse;
    }
}
