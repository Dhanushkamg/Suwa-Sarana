package com.suwasarana.api.geo;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DistrictTest {

    @Test
    void thereAreExactlyTwentyFiveDistinctDistricts() {
        assertEquals(25, District.all().size());
        assertEquals(25, new HashSet<>(District.names()).size());
    }

    @Test
    void everyDistrictCentreIsInsideSriLanka() {
        for (District.Entry d : District.all()) {
            assertTrue(GeoBounds.contains(d.latitude(), d.longitude()), d.name() + " centre must be inside the bounding box");
        }
    }

    @Test
    void normalisationIgnoresCaseSpacingAndDistrictSuffix() {
        assertEquals(Optional.of("Colombo"), District.normalise("colombo"));
        assertEquals(Optional.of("Colombo"), District.normalise("  COLOMBO  "));
        assertEquals(Optional.of("Colombo"), District.normalise("Colombo District"));
        assertEquals(Optional.of("Colombo"), District.normalise("colombo   district"));
        assertEquals(Optional.of("Nuwara Eliya"), District.normalise("nuwaraeliya"));
        assertEquals(Optional.of("Nuwara Eliya"), District.normalise("Nuwara-Eliya District"));
        assertEquals(Optional.of("Monaragala"), District.normalise("Moneragala"));
        assertEquals(Optional.of("Mullaitivu"), District.normalise("Mullativu"));
        assertEquals(Optional.of("Hambantota"), District.normalise("Hambanthota"));
    }

    @Test
    void everyCanonicalNameNormalisesToItself() {
        for (String name : District.names()) {
            assertEquals(Optional.of(name), District.normalise(name));
            assertEquals(Optional.of(name), District.normalise(name.toUpperCase() + " District"));
        }
    }

    @Test
    void unknownOrBlankInputIsNeverMappedToAnotherDistrict() {
        assertEquals(Optional.empty(), District.normalise("Atlantis"));
        assertEquals(Optional.empty(), District.normalise("District"));
        assertEquals(Optional.empty(), District.normalise(""));
        assertEquals(Optional.empty(), District.normalise("   "));
        assertEquals(Optional.empty(), District.normalise(null));
        assertEquals(Optional.empty(), District.normalise("Colombo Gampaha"));
    }

    @Test
    void boundingBoxRejectsTheUsualMistakes() {
        assertFalse(GeoBounds.contains(0, 0), "null island");
        assertFalse(GeoBounds.contains(79.86, 6.93), "swapped lat/lon");
        assertFalse(GeoBounds.contains(13.08, 80.27), "Chennai");
        assertTrue(GeoBounds.contains(6.9271, 79.8612), "Colombo");
        assertTrue(GeoBounds.contains(9.6615, 80.0255), "Jaffna");
        assertTrue(GeoBounds.contains(5.9549, 80.5550), "Matara");
    }

    @Test
    void clampKeepsValuesInsideTheBox() {
        assertEquals(GeoBounds.MIN_LAT, GeoBounds.clampLat(-5));
        assertEquals(GeoBounds.MAX_LAT, GeoBounds.clampLat(50));
        assertEquals(GeoBounds.MIN_LON, GeoBounds.clampLon(0));
        assertEquals(GeoBounds.MAX_LON, GeoBounds.clampLon(120));
        assertEquals(7.5, GeoBounds.clampLat(7.5));
    }

    @Test
    void coarseRoundingIsAboutOneKilometre() {
        assertEquals(6.93, GeoBounds.round(6.92714, 2));
        assertEquals(79.86, GeoBounds.round(79.86121, 2));
    }
}
