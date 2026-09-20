package com.suwasarana.api.user;

import java.time.Year;

public class NicValidator {

    /**
     * Validates a Sri Lankan NIC number.
     * Supports both old format (9 digits + V/X) and new format (12 digits).
     *
     * @param nic the NIC string to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValid(String nic) {
        if (nic == null || nic.trim().isEmpty()) {
            return false;
        }

        nic = nic.trim().toUpperCase();

        if (nic.matches("^[0-9]{9}[VX]$")) {
            return validateOldNic(nic);
        } else if (nic.matches("^[0-9]{12}$")) {
            return validateNewNic(nic);
        }

        return false;
    }

    private static boolean validateOldNic(String nic) {
        int year = Integer.parseInt(nic.substring(0, 2));
        int days = Integer.parseInt(nic.substring(2, 5));
        
        // Adjust for women
        if (days > 500) {
            days -= 500;
        }

        // Days must be between 1 and 366 (leap year)
        if (days < 1 || days > 366) {
            return false;
        }

        return true;
    }

    private static boolean validateNewNic(String nic) {
        int year = Integer.parseInt(nic.substring(0, 4));
        int days = Integer.parseInt(nic.substring(4, 7));

        // Basic year check (e.g. not before 1900 and not in the future)
        int currentYear = Year.now().getValue();
        if (year < 1900 || year > currentYear) {
            return false;
        }

        // Adjust for women
        if (days > 500) {
            days -= 500;
        }

        // Days must be between 1 and 366 (leap year)
        if (days < 1 || days > 366) {
            return false;
        }

        return true;
    }
}
