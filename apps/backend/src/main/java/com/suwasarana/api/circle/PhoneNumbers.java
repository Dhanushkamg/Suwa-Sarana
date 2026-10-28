package com.suwasarana.api.circle;

import java.util.regex.Pattern;

/** Normalises guest phone numbers to E.164 and rejects obviously invalid ones. */
final class PhoneNumbers {

    private static final Pattern E164 = Pattern.compile("^\\+[1-9][0-9]{7,14}$");
    private static final Pattern REPEATED = Pattern.compile("^(\\d)\\1+$");
    private static final Pattern DIGITS = Pattern.compile("^\\+?[0-9]+$");

    private PhoneNumbers() {
    }

    /**
     * Accepts Sri Lankan numbers in local ({@code 0771234567}), national-prefix-less ({@code 771234567}),
     * {@code 94771234567}, {@code +94771234567} and {@code 0094...} form, and other international numbers written
     * with a leading {@code +} or {@code 00}. Spaces, dashes, dots and brackets are ignored.
     *
     * @return the number as {@code +<country><digits>}, or {@code null} when it is not a plausible phone number
     */
    static String normalise(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim().replaceAll("[\\s\\-().]", "");
        if (!DIGITS.matcher(s).matches()) {
            return null;
        }
        String candidate;
        if (s.startsWith("+")) {
            candidate = s;
        } else if (s.startsWith("00")) {
            candidate = "+" + s.substring(2);
        } else if (s.startsWith("94") && s.length() == 11) {
            candidate = "+" + s;
        } else if (s.startsWith("0") && s.length() == 10) {
            candidate = "+94" + s.substring(1);
        } else if (s.length() == 9 && s.charAt(0) != '0') {
            candidate = "+94" + s;
        } else {
            return null;
        }
        if (!E164.matcher(candidate).matches()) {
            return null;
        }
        String digits = candidate.substring(1);
        boolean sriLanka = digits.startsWith("94");
        if (sriLanka && digits.length() != 11) {
            return null; // Sri Lankan numbers have exactly 9 digits after the country code
        }
        if (REPEATED.matcher(sriLanka ? digits.substring(2) : digits).matches()) {
            return null; // 000000000, 777777777 ...
        }
        return candidate;
    }
}
