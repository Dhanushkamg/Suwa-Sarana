package com.suwasarana.api.auth;

/** Turns a Google ID token into a verified identity. The default implementation talks to Google; tests plug in a fake. */
public interface GoogleIdentityVerifier {

    /**
     * @return the identity from a token whose signature, audience and expiry were verified and whose email Google
     *         has verified
     * @throws AuthException 401 auth.google.invalid for a bad token or unverified email; 502 auth.google.unavailable
     *         when Google's keys cannot be fetched
     */
    GoogleIdentity verify(String idToken);

    record GoogleIdentity(String email, String subject) {
    }
}
