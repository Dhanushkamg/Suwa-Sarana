package com.suwasarana.api.matching;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ABO / Rh compatibility for red-cell (whole blood) donations: which donor types may give to which patient type.
 *
 * <p>Rule: a donor's red cells are compatible with a patient when every antigen on the donor's cells (A, B, RhD) is
 * also present on the patient's own cells. So O- gives to everyone, AB+ receives from everyone, A+ receives from
 * A+, A-, O+ and O-, and an Rh-negative patient can only receive Rh-negative cells.
 * Platelet and plasma rules differ; the platform only matches donors for whole blood / red cells.
 */
public final class BloodCompatibility {

    /** All eight types, ordered from most to least universally donatable (used for deterministic output). */
    public static final List<String> TYPES = List.of("O-", "O+", "A-", "A+", "B-", "B+", "AB-", "AB+");

    private static final Map<String, Set<Character>> ANTIGENS = new LinkedHashMap<>();
    private static final Map<String, List<String>> DONORS_FOR_PATIENT = new LinkedHashMap<>();

    static {
        for (String t : TYPES) {
            ANTIGENS.put(t, antigensOf(t));
        }
        for (String patient : TYPES) {
            List<String> donors = new ArrayList<>();
            donors.add(patient); // the exact type always comes first
            for (String donor : TYPES) {
                if (!donor.equals(patient) && ANTIGENS.get(patient).containsAll(ANTIGENS.get(donor))) {
                    donors.add(donor);
                }
            }
            DONORS_FOR_PATIENT.put(patient, List.copyOf(donors));
        }
    }

    private BloodCompatibility() {}

    private static Set<Character> antigensOf(String type) {
        Set<Character> antigens = new java.util.HashSet<>();
        if (type.startsWith("AB")) {
            antigens.add('A');
            antigens.add('B');
        } else if (type.startsWith("A")) {
            antigens.add('A');
        } else if (type.startsWith("B")) {
            antigens.add('B');
        }
        if (type.endsWith("+")) {
            antigens.add('D');
        }
        return antigens;
    }

    public static boolean isValid(String type) {
        return type != null && ANTIGENS.containsKey(type);
    }

    /** True when a donor of {@code donorType} may give red cells to a patient of {@code patientType}. */
    public static boolean canDonate(String donorType, String patientType) {
        if (!isValid(donorType) || !isValid(patientType)) return false;
        return ANTIGENS.get(patientType).containsAll(ANTIGENS.get(donorType));
    }

    /** Donor types that may give to this patient type; the patient's own type is first. Empty for an invalid type. */
    public static List<String> compatibleDonorTypes(String patientType) {
        return DONORS_FOR_PATIENT.getOrDefault(patientType, List.of());
    }
}
