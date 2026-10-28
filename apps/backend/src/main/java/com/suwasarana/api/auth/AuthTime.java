package com.suwasarana.api.auth;

import java.time.LocalDateTime;

/**
 * The ONE place the auth module reads wall-clock time for LocalDateTime columns (OTP records, user created/updated).
 * When the platform clock helper (Times.utcNow(clock)) is merged, change only {@link #now()}.
 */
public final class AuthTime {

    private AuthTime() {
    }

    public static LocalDateTime now() {
        return LocalDateTime.now();
    }
}
