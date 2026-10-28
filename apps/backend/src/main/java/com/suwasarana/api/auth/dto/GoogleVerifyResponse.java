package com.suwasarana.api.auth.dto;

/**
 * Result of /auth/google/verify. Exactly one of three situations:
 * signed in (authResponse set), first visit (requiresRegistration), or the email already has a verified password
 * account that must be linked explicitly with its password via /auth/google/link (requiresLinking).
 */
public class GoogleVerifyResponse {
    private boolean requiresRegistration;
    private boolean requiresLinking;
    private String email;
    private AuthResponse authResponse;

    public GoogleVerifyResponse(boolean requiresRegistration, String email, AuthResponse authResponse) {
        this(requiresRegistration, false, email, authResponse);
    }

    public GoogleVerifyResponse(boolean requiresRegistration, boolean requiresLinking, String email, AuthResponse authResponse) {
        this.requiresRegistration = requiresRegistration;
        this.requiresLinking = requiresLinking;
        this.email = email;
        this.authResponse = authResponse;
    }

    public boolean isRequiresRegistration() { return requiresRegistration; }
    public void setRequiresRegistration(boolean requiresRegistration) { this.requiresRegistration = requiresRegistration; }
    public boolean isRequiresLinking() { return requiresLinking; }
    public void setRequiresLinking(boolean requiresLinking) { this.requiresLinking = requiresLinking; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public AuthResponse getAuthResponse() { return authResponse; }
    public void setAuthResponse(AuthResponse authResponse) { this.authResponse = authResponse; }
}
