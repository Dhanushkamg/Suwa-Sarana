package com.suwasarana.api.auth.dto;

import com.suwasarana.api.auth.AuthCodes;
import jakarta.validation.constraints.NotBlank;

/** Explicitly links a Google identity to an existing, email-verified password account: proves control of both. */
public class GoogleLinkRequest {
    @NotBlank(message = AuthCodes.GOOGLE_INVALID)
    private String idToken;

    @NotBlank(message = AuthCodes.PASSWORD_REQUIRED)
    private String password;

    public String getIdToken() { return idToken; }
    public void setIdToken(String idToken) { this.idToken = idToken; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
