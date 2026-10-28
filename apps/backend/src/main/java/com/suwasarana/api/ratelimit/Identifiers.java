package com.suwasarana.api.ratelimit;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Locale;

/** Extracts and normalises the "who is this request about" identifier used for per-identifier limits. */
final class Identifiers {

    /** JSON fields that carry an account identifier, in order of preference. */
    static final List<String> JSON_FIELDS = List.of(
            "phoneNumber", "phone", "mobile", "email", "username", "identifier");

    private Identifiers() {
    }

    /** First identifier found in a JSON object, normalised; {@code null} when there is none. */
    static String fromJson(JsonNode root) {
        if (root == null || !root.isObject()) {
            return null;
        }
        for (String field : JSON_FIELDS) {
            JsonNode node = root.get(field);
            if (node != null && node.isTextual() && !node.asText().isBlank()) {
                return normalise(node.asText());
            }
        }
        return null;
    }

    /**
     * E-mail addresses are compared case-insensitively; anything that looks like a phone number is reduced to
     * its national significant digits, so {@code 0771234567}, {@code +94 77 123 4567} and {@code 94771234567}
     * share one bucket.
     */
    static String normalise(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return null;
        }
        if (s.indexOf('@') >= 0) {
            return "e:" + s.toLowerCase(Locale.ROOT);
        }
        String digits = s.replaceAll("\\D", "");
        if (digits.length() >= 7) {
            if (digits.startsWith("0094")) {
                digits = digits.substring(4);
            } else if (digits.startsWith("94") && digits.length() == 11) {
                digits = digits.substring(2);
            } else if (digits.startsWith("0") && digits.length() == 10) {
                digits = digits.substring(1);
            }
            return "p:" + digits;
        }
        return "u:" + s.toLowerCase(Locale.ROOT);
    }
}
