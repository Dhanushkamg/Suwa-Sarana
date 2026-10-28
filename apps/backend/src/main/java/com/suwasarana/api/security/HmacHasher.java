package com.suwasarana.api.security;

import com.suwasarana.api.auth.AuthProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Keyed hashing (HMAC-SHA256) for values that must be comparable but not readable at rest: OTP codes and NIC numbers.
 * The key is the server secret AUTH_HMAC_SECRET; a database leak alone cannot be brute-forced offline.
 * Each use passes a {@code purpose} so a hash made for one purpose is useless for another.
 */
@Component
public class HmacHasher {

    private final byte[] key;

    public HmacHasher(AuthProperties properties) {
        this.key = properties.getHmacSecret().getBytes(StandardCharsets.UTF_8);
    }

    public String hash(String purpose, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            byte[] out = mac.doFinal((purpose + ":" + value).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(out);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }

    /** Constant-time comparison of two hex digests. */
    public static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
