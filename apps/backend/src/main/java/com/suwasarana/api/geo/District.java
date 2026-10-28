package com.suwasarana.api.geo;

import java.text.Normalizer;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The 25 official districts of Sri Lanka, with a representative centre point for each.
 *
 * <p>This is the single source of truth for district names across the backend. Free text from clients is
 * normalised through {@link #normalise(String)} (case/space-insensitive, tolerates a "District" suffix and a few
 * well-known spelling variants) so "colombo", "Colombo District" and " COLOMBO " are all the same district.
 * Unknown input yields {@link Optional#empty()}: callers must reject it (or report it as "unmapped"), never
 * silently move it to another district.
 */
public final class District {

    /** Canonical name, centre latitude, centre longitude (district headquarters / city centre). */
    public record Entry(String name, double latitude, double longitude) {}

    private static final Map<String, Entry> BY_NAME;
    private static final Map<String, String> BY_KEY = new HashMap<>();

    static {
        Map<String, Entry> m = new LinkedHashMap<>();
        add(m, "Colombo", 6.9271, 79.8612);
        add(m, "Gampaha", 7.0840, 80.0098);
        add(m, "Kalutara", 6.5854, 79.9607);
        add(m, "Kandy", 7.2906, 80.6337);
        add(m, "Matale", 7.4675, 80.6234);
        add(m, "Nuwara Eliya", 6.9497, 80.7891);
        add(m, "Galle", 6.0535, 80.2210);
        add(m, "Matara", 5.9549, 80.5550);
        add(m, "Hambantota", 6.1429, 81.1212);
        add(m, "Jaffna", 9.6615, 80.0255);
        add(m, "Kilinochchi", 9.3803, 80.3770);
        add(m, "Mannar", 8.9810, 79.9044);
        add(m, "Vavuniya", 8.7542, 80.4982);
        add(m, "Mullaitivu", 9.2671, 80.8143);
        add(m, "Batticaloa", 7.7310, 81.6747);
        add(m, "Ampara", 7.2912, 81.6724);
        add(m, "Trincomalee", 8.5874, 81.2152);
        add(m, "Kurunegala", 7.4863, 80.3623);
        add(m, "Puttalam", 8.0408, 79.8394);
        add(m, "Anuradhapura", 8.3114, 80.4037);
        add(m, "Polonnaruwa", 7.9403, 81.0188);
        add(m, "Badulla", 6.9934, 81.0550);
        add(m, "Monaragala", 6.8728, 81.3507);
        add(m, "Ratnapura", 6.6828, 80.3992);
        add(m, "Kegalle", 7.2513, 80.3464);
        BY_NAME = Collections.unmodifiableMap(m);

        for (Entry e : m.values()) {
            BY_KEY.put(key(e.name()), e.name());
        }
        // Common alternative spellings (transliteration variants seen in official and press material)
        alias("Moneragala", "Monaragala");
        alias("Mulativu", "Mullaitivu");
        alias("Mullativu", "Mullaitivu");
        alias("Mulaitivu", "Mullaitivu");
        alias("Kilinochi", "Kilinochchi");
        alias("Trinco", "Trincomalee");
        alias("Anuradapura", "Anuradhapura");
        alias("Polonaruwa", "Polonnaruwa");
        alias("Hambanthota", "Hambantota");
    }

    private District() {}

    private static void add(Map<String, Entry> m, String name, double lat, double lon) {
        m.put(name, new Entry(name, lat, lon));
    }

    private static void alias(String variant, String canonical) {
        BY_KEY.put(key(variant), canonical);
    }

    /** Lower-cased key with accents, punctuation and spacing removed. */
    private static String key(String s) {
        String n = Normalizer.normalize(s, Normalizer.Form.NFKD).replaceAll("\\p{M}", "");
        return n.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
    }

    /** All 25 districts in a stable order. */
    public static List<Entry> all() {
        return List.copyOf(BY_NAME.values());
    }

    public static List<String> names() {
        return List.copyOf(BY_NAME.keySet());
    }

    public static boolean isCanonical(String name) {
        return name != null && BY_NAME.containsKey(name);
    }

    public static Entry get(String canonicalName) {
        return BY_NAME.get(canonicalName);
    }

    /**
     * Maps free text to a canonical district name. Case, spacing, punctuation and a trailing "District" are
     * ignored. Returns empty for blank or unknown input.
     */
    public static Optional<String> normalise(String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        String k = key(raw);
        String suffix = "district";
        if (k.endsWith(suffix) && k.length() > suffix.length()) {
            k = k.substring(0, k.length() - suffix.length());
        }
        return Optional.ofNullable(BY_KEY.get(k));
    }

    /** Name to coordinates view (kept for code that predates this class, e.g. the AI intake service). */
    public static Map<String, double[]> coordinateMap() {
        Map<String, double[]> m = new LinkedHashMap<>();
        BY_NAME.values().forEach(e -> m.put(e.name(), new double[]{e.latitude(), e.longitude()}));
        return Collections.unmodifiableMap(m);
    }
}
