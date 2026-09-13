package com.suwasarana.api.auth.dto;

import jakarta.validation.constraints.NotBlank;

public class GoogleVerifyRequest {
    @NotBlank(message = "ID Token is required")
    private String idToken;

    public String getIdToken() {
        return idToken;
    }

    public void setIdToken(String idToken) {
        this.idToken = idToken;
    }
}
