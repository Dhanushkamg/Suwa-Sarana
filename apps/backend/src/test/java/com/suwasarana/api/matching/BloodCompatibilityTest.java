package com.suwasarana.api.matching;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BloodCompatibilityTest {

    /**
     * The standard red-cell compatibility chart, written out by hand (patient type -> donor types that may give to it).
     * It is deliberately NOT derived from the antigen rule used by the implementation, so the two can disagree.
     */
    private static final Map<String, Set<String>> CHART = Map.of(
            "O-", Set.of("O-"),
            "O+", Set.of("O-", "O+"),
            "A-", Set.of("O-", "A-"),
            "A+", Set.of("O-", "O+", "A-", "A+"),
            "B-", Set.of("O-", "B-"),
            "B+", Set.of("O-", "O+", "B-", "B+"),
            "AB-", Set.of("O-", "A-", "B-", "AB-"),
            "AB+", Set.of("O-", "O+", "A-", "A+", "B-", "B+", "AB-", "AB+"));

    @Test
    @DisplayName("All 64 donor/patient pairs agree with the compatibility chart")
    void fullEightByEightMatrix() {
        int compatiblePairs = 0;
        for (String patient : BloodCompatibility.TYPES) {
            for (String donor : BloodCompatibility.TYPES) {
                boolean expected = CHART.get(patient).contains(donor);
                assertEquals(expected, BloodCompatibility.canDonate(donor, patient),
                        donor + " -> " + patient + " should be " + (expected ? "compatible" : "incompatible"));
                if (expected) compatiblePairs++;
            }
        }
        // 1 + 2 + 2 + 4 + 2 + 4 + 4 + 8 compatible pairs in the chart
        assertEquals(27, compatiblePairs);
    }

    @Test
    @DisplayName("compatibleDonorTypes lists exactly the chart row, with the patient's own type first")
    void donorListsMatchChart() {
        for (String patient : BloodCompatibility.TYPES) {
            List<String> donors = BloodCompatibility.compatibleDonorTypes(patient);
            assertEquals(CHART.get(patient), Set.copyOf(donors), "donors for " + patient);
            assertEquals(donors.size(), Set.copyOf(donors).size(), "no duplicates for " + patient);
            assertEquals(patient, donors.get(0), "exact type first for " + patient);
        }
    }

    @Test
    void oNegativeIsTheUniversalDonor_abPositiveTheUniversalRecipient() {
        for (String patient : BloodCompatibility.TYPES) {
            assertTrue(BloodCompatibility.canDonate("O-", patient));
            assertTrue(BloodCompatibility.canDonate(patient, "AB+"));
        }
    }

    @Test
    void rhPositiveCellsNeverGoToRhNegativePatients() {
        for (String donor : List.of("O+", "A+", "B+", "AB+")) {
            for (String patient : List.of("O-", "A-", "B-", "AB-")) {
                assertFalse(BloodCompatibility.canDonate(donor, patient), donor + " -> " + patient);
            }
        }
    }

    @Test
    void invalidTypesAreRejected() {
        assertFalse(BloodCompatibility.canDonate("C+", "O+"));
        assertFalse(BloodCompatibility.canDonate("O+", null));
        assertFalse(BloodCompatibility.isValid("o+"));
        assertTrue(BloodCompatibility.compatibleDonorTypes("X").isEmpty());
    }
}
