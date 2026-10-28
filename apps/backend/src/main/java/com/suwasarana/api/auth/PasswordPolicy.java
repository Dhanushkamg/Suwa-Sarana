package com.suwasarana.api.auth;

import com.suwasarana.api.user.PhoneNumbers;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Password rules: 10-128 characters, not a well-known password (denylist plus simple variants such as
 * "Password123!" or "p@ssw0rd"), not a trivial pattern (all one character, 1234567890, abcdefghij) and not equal to
 * the account's own email, email name or phone number. There are deliberately no "must contain a symbol" rules:
 * length and the denylist matter more (NIST SP 800-63B).
 */
@Component
public class PasswordPolicy {

    public static final int MIN_LENGTH = 10;
    public static final int MAX_LENGTH = 128;

    private final Set<String> common = loadCommon();

    /**
     * @param email the account's email (may be null)
     * @param phone the account's phone in any spelling (may be null)
     * @return null when the password is acceptable, otherwise the error code from {@link AuthCodes}
     */
    public String violation(String password, String email, String phone) {
        if (password == null || password.isBlank()) {
            return AuthCodes.PASSWORD_REQUIRED;
        }
        if (password.length() < MIN_LENGTH) {
            return AuthCodes.PASSWORD_TOO_SHORT;
        }
        if (password.length() > MAX_LENGTH) {
            return AuthCodes.PASSWORD_TOO_LONG;
        }
        if (sameAsIdentifier(password, email, phone)) {
            return AuthCodes.PASSWORD_SAME_AS_IDENTIFIER;
        }
        if (isCommon(password)) {
            return AuthCodes.PASSWORD_COMMON;
        }
        return null;
    }

    /** Throws a 400 with {@code data: {fieldName: code}} when the password is unacceptable. */
    public void enforce(String fieldName, String password, String email, String phone) {
        String code = violation(password, email, phone);
        if (code != null) {
            throw AuthException.invalidField(fieldName, code);
        }
    }

    boolean isCommon(String password) {
        String lower = password.toLowerCase(Locale.ROOT);
        if (inList(lower)) {
            return true;
        }
        // "password2024!" -> "password": strip trailing digits/punctuation (and leading digits) and look again
        String stripped = lower.replaceAll("[^a-z]+$", "");
        if (stripped.length() >= 4 && inList(stripped)) {
            return true;
        }
        String core = stripped.replaceAll("^[^a-z]+", "");
        if (core.length() >= 4 && inList(core)) {
            return true;
        }
        return isTrivialPattern(lower);
    }

    /** Exact entry, or the entry after undoing leetspeak (p@ssw0rd -> password). */
    private boolean inList(String s) {
        return common.contains(s) || common.contains(unLeet(s));
    }

    private static boolean sameAsIdentifier(String password, String email, String phone) {
        String p = password.trim().toLowerCase(Locale.ROOT);
        if (email != null && !email.isBlank()) {
            String e = email.trim().toLowerCase(Locale.ROOT);
            int at = e.indexOf('@');
            if (p.equals(e) || (at > 0 && p.equals(e.substring(0, at)))) {
                return true;
            }
        }
        if (phone != null && !phone.isBlank()) {
            for (String variant : PhoneNumbers.variants(phone)) {
                String v = variant.toLowerCase(Locale.ROOT);
                if (p.equals(v) || p.equals(v.replace("+", ""))) {
                    return true;
                }
            }
            if (p.equals(phone.trim().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isTrivialPattern(String s) {
        if (s.chars().distinct().count() <= 2) {
            return true; // aaaaaaaaaa, abababababab
        }
        return isSequence(s, 1) || isSequence(s, -1);
    }

    /** 1234567890 / abcdefghij (step +1) or 9876543210 (step -1). */
    private static boolean isSequence(String s, int step) {
        for (int i = 1; i < s.length(); i++) {
            int prev = s.charAt(i - 1);
            int cur = s.charAt(i);
            boolean wrap = prev == '9' && cur == '0' && step == 1; // ...890
            if (cur - prev != step && !wrap) {
                return false;
            }
        }
        return true;
    }

    private static String unLeet(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            switch (c) {
                case '@': case '4': sb.append('a'); break;
                case '0': sb.append('o'); break;
                case '1': case '!': sb.append('i'); break;
                case '3': sb.append('e'); break;
                case '$': case '5': sb.append('s'); break;
                case '7': sb.append('t'); break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }

    private static Set<String> loadCommon() {
        Set<String> set = new HashSet<>();
        try (InputStream in = PasswordPolicy.class.getResourceAsStream("/common-passwords.txt")) {
            if (in == null) {
                throw new IllegalStateException("common-passwords.txt is missing from the classpath");
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String w = line.trim().toLowerCase(Locale.ROOT);
                    if (!w.isEmpty() && !w.startsWith("#")) {
                        set.add(w);
                        set.add(unLeet(w));
                    }
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read common-passwords.txt", e);
        }
        return set;
    }
}
