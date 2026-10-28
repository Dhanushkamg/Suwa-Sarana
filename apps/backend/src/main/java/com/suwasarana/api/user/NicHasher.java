package com.suwasarana.api.user;

import com.suwasarana.api.security.HmacHasher;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * The NIC is only ever needed to notice that the same person registers twice, so it is not stored: we keep
 * HMAC-SHA256(server secret, normalised NIC). The server secret makes the hash useless to anyone holding a database dump
 * (NIC numbers are guessable without a key: they are a birth year, a day number and a serial).
 */
@Component
public class NicHasher {

    private static final String PURPOSE = "nic";

    private final HmacHasher hasher;

    public NicHasher(HmacHasher hasher) {
        this.hasher = hasher;
    }

    /** Upper-cased, trimmed NIC; null for blank input. */
    public static String normalize(String nic) {
        if (nic == null || nic.isBlank()) {
            return null;
        }
        return nic.trim().toUpperCase(Locale.ROOT);
    }

    /** Keyed hash of the NIC, or null when no NIC was given. */
    public String hash(String nic) {
        String normalised = normalize(nic);
        return normalised == null ? null : hasher.hash(PURPOSE, normalised);
    }
}
