package com.suwasarana.api.circle;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Creation, hashing and reversible protection of circle invite tokens.
 * <ul>
 *   <li>The token is 256 bits from {@link SecureRandom}, shown as 64 hex characters.</li>
 *   <li>The database only stores its SHA-256 (lookup) and an AES-256-GCM copy (so the requester can fetch the link
 *       again). A database dump alone therefore reveals no usable link.</li>
 * </ul>
 */
@Component
public class CircleTokens {

    private static final String PREFIX = "v1:";
    private static final int NONCE_BYTES = 12;

    private final SecureRandom random = new SecureRandom();
    private final SecretKeySpec key;

    @Autowired
    public CircleTokens(CircleProperties properties, @Value("${jwt.secret:}") String jwtSecret) {
        this(properties.getTokenKey(), jwtSecret);
    }

    public CircleTokens(String tokenKey, String jwtSecret) {
        boolean hasKey = tokenKey != null && !tokenKey.isBlank();
        boolean hasJwt = jwtSecret != null && !jwtSecret.isBlank();
        if (!hasKey && !hasJwt) {
            throw new IllegalStateException("Set app.circle.token-key (CIRCLE_TOKEN_KEY) or jwt.secret to protect circle invite tokens");
        }
        String material = hasKey ? "key|" + tokenKey : "jwt|" + jwtSecret;
        byte[] derived = sha256(("suwa-sarana/circle-token-key/v1|" + material).getBytes(StandardCharsets.UTF_8));
        this.key = new SecretKeySpec(derived, "AES");
    }

    public String generate() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /** SHA-256 (hex) of the token; {@code null} for a null / blank token. */
    public String hash(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        return HexFormat.of().formatHex(sha256(token.getBytes(StandardCharsets.UTF_8)));
    }

    public String encrypt(String token) {
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));
            byte[] ct = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
            byte[] out = Arrays.copyOf(nonce, nonce.length + ct.length);
            System.arraycopy(ct, 0, out, nonce.length, ct.length);
            return PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not protect circle token", e);
        }
    }

    /** The original token, or empty when the value is missing, malformed or was written under another key. */
    public Optional<String> decrypt(String stored) {
        if (stored == null || !stored.startsWith(PREFIX)) {
            return Optional.empty();
        }
        try {
            byte[] in = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            if (in.length <= NONCE_BYTES) {
                return Optional.empty();
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, Arrays.copyOf(in, NONCE_BYTES)));
            byte[] pt = cipher.doFinal(in, NONCE_BYTES, in.length - NONCE_BYTES);
            return Optional.of(new String(pt, StandardCharsets.UTF_8));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
