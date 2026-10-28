package com.suwasarana.api.auth.dto;

import com.suwasarana.api.auth.AuthCodes;
import jakarta.validation.constraints.NotBlank;

public class GoogleVerifyRequest {
    @NotBlank(message = AuthCodes.GOOGLE_INVALID)
    private String idToken;

    public String getIdToken() {
        return idToken;
    }

    public void setIdToken(String idToken) {
        this.idToken = idToken;
    }
}
