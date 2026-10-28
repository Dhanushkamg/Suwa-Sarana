package com.suwasarana.api.user;

import java.util.ArrayList;
import java.util.List;

/**
 * The single place that knows how phone numbers look. Everything stored or compared is E.164 ("+947XXXXXXXXX").
 * Users still type "077 123 4567", "0771234567", "94771234567" or "+94 77 123 4567"; all of those normalise to
 * the same value. Only Sri Lankan mobile numbers can be registered (OTP is delivered by SMS).
 */
public final class PhoneNumbers {

    /** The only shape a stored/validated phone number may have. */
    public static final String MOBILE_E164_REGEX = "^\\+947[0-9]{8}$";

    private PhoneNumbers() {
    }

    /**
     * Normalises a Sri Lankan number to E.164.
     *
     * @return "+94XXXXXXXXX", or {@code null} when the input is blank or not a Sri Lankan number
     */
    public static String normalize(String input) {
        if (input == null) {
            return null;
        }
        String digits = input.trim().replaceAll("[\\s\\-().]", "");
        boolean plus = digits.startsWith("+");
        if (plus) {
            digits = digits.substring(1);
        }
        if (digits.isEmpty() || !digits.chars().allMatch(c -> c >= '0' && c <= '9')) {
            return null;
        }
        String national;
        if (digits.startsWith("0094") && digits.length() == 13) {
            national = digits.substring(4);
        } else if (digits.startsWith("94") && digits.length() == 11) {
            national = digits.substring(2);
        } else if (!plus && digits.startsWith("0") && digits.length() == 10) {
            national = digits.substring(1);
        } else if (!plus && digits.length() == 9) {
            national = digits;
        } else {
            return null;
        }
        if (national.length() != 9 || national.charAt(0) == '0') {
            return null;
        }
        return "+94" + national;
    }

    /** Normalised value when the input is a valid Sri Lankan number, otherwise the trimmed input unchanged (so validation can reject it). */
    public static String normalizeOrKeep(String input) {
        String normalised = normalize(input);
        if (normalised != null) {
            return normalised;
        }
        return input == null ? null : input.trim();
    }

    /** True for "+947XXXXXXXX": a number an SMS can reach. */
    public static boolean isMobile(String e164) {
        return e164 != null && e164.matches(MOBILE_E164_REGEX);
    }

    /**
     * Every spelling under which the same number may be stored: E.164 first, then the legacy local form
     * (0XXXXXXXXX). Used for lookups that must still find rows written before migration V25.
     */
    public static List<String> variants(String input) {
        List<String> out = new ArrayList<>(2);
        String e164 = normalize(input);
        if (e164 == null) {
            if (input != null && !input.isBlank()) {
                out.add(input.trim());
            }
            return out;
        }
        out.add(e164);
        out.add("0" + e164.substring(3));
        return out;
    }

    /** "+94771234567" -> "+9477*****567" style mask for logs: never log a whole number. */
    public static String mask(String phone) {
        if (phone == null || phone.isBlank()) {
            return "<none>";
        }
        String p = phone.trim();
        if (p.length() <= 6) {
            return "*****";
        }
        int keepStart = p.startsWith("+") ? 3 : 2;
        return p.substring(0, Math.min(keepStart, p.length())) + "*****" + p.substring(p.length() - 3);
    }
}
