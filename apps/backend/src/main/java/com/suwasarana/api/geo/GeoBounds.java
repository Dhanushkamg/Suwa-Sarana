package com.suwasarana.api.geo;

/**
 * Bounding box that contains Sri Lanka (mainland, Jaffna islands, Mannar island) with a small margin.
 * Coordinates outside it cannot be a hospital or donor in Sri Lanka, so they are rejected on input
 * (0,0 and swapped lat/lon are the usual mistakes) and clamped where a value must be forced into range.
 */
public final class GeoBounds {

    public static final double MIN_LAT = 5.7;
    public static final double MAX_LAT = 10.0;
    public static final double MIN_LON = 79.4;
    public static final double MAX_LON = 82.1;

    /** String forms for Bean Validation annotations (they need compile-time constants). */
    public static final String MIN_LAT_STR = "5.7";
    public static final String MAX_LAT_STR = "10.0";
    public static final String MIN_LON_STR = "79.4";
    public static final String MAX_LON_STR = "82.1";

    public static final String LAT_MESSAGE = "Latitude must be within Sri Lanka (5.7 to 10.0)";
    public static final String LON_MESSAGE = "Longitude must be within Sri Lanka (79.4 to 82.1)";

    private GeoBounds() {}

    public static boolean contains(double lat, double lon) {
        return lat >= MIN_LAT && lat <= MAX_LAT && lon >= MIN_LON && lon <= MAX_LON;
    }

    public static double clampLat(double lat) {
        return Math.max(MIN_LAT, Math.min(MAX_LAT, lat));
    }

    public static double clampLon(double lon) {
        return Math.max(MIN_LON, Math.min(MAX_LON, lon));
    }

    /** Rounds to {@code decimals} places; 2 places is about 1 km, which is "coarse" for public lists. */
    public static double round(double value, int decimals) {
        double f = Math.pow(10, decimals);
        return Math.round(value * f) / f;
    }
}
