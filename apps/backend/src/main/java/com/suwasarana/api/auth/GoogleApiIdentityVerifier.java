package com.suwasarana.api.auth;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.Locale;

/** Verifies Google ID tokens with Google's public keys (signature, audience = our client id, expiry, email_verified). */
@Component
public class GoogleApiIdentityVerifier implements GoogleIdentityVerifier {

    private final String googleClientId;

    public GoogleApiIdentityVerifier(@Value("${app.google.client-id}") String googleClientId) {
        this.googleClientId = googleClientId;
    }

    @Override
    public GoogleIdentity verify(String idTokenString) {
        GoogleIdToken idToken;
        try {
            idToken = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                    .setAudience(Collections.singletonList(googleClientId))
                    .build()
                    .verify(idTokenString);
        } catch (GeneralSecurityException | IOException e) {
            throw new AuthException(HttpStatus.BAD_GATEWAY, AuthCodes.GOOGLE_UNAVAILABLE);
        } catch (IllegalArgumentException e) {
            throw AuthException.unauthorized(AuthCodes.GOOGLE_INVALID); // malformed token string
        }
        if (idToken == null || !Boolean.TRUE.equals(idToken.getPayload().getEmailVerified())
                || idToken.getPayload().getEmail() == null) {
            throw AuthException.unauthorized(AuthCodes.GOOGLE_INVALID);
        }
        return new GoogleIdentity(idToken.getPayload().getEmail().trim().toLowerCase(Locale.ROOT), idToken.getPayload().getSubject());
    }
}
