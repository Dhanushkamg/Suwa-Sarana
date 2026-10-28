package com.suwasarana.api.user;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PhoneNumbersTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "0771234567|+94771234567",
            "077 123 4567|+94771234567",
            "077-123-4567|+94771234567",
            "771234567|+94771234567",
            "94771234567|+94771234567",
            "+94771234567|+94771234567",
            "+94 77 123 4567|+94771234567",
            "0094771234567|+94771234567",
            "(077) 123 4567|+94771234567",
            "0112369931|+94112369931"
    })
    void everyCommonSpellingNormalisesToE164(String input, String expected) {
        assertEquals(expected, PhoneNumbers.normalize(input));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "abc", "12345", "+1 202 555 0100", "+9477123456", "07712345678", "0471234567x", "+947712345678"})
    void notASriLankanNumber_isNull(String input) {
        assertNull(PhoneNumbers.normalize(input));
    }

    @Test
    void onlyMobileNumbersCanReceiveAnOtp() {
        assertTrue(PhoneNumbers.isMobile("+94771234567"));
        assertFalse(PhoneNumbers.isMobile("+94112369931")); // landline
        assertFalse(PhoneNumbers.isMobile("0771234567"));    // not E.164
        assertFalse(PhoneNumbers.isMobile(null));
    }

    @Test
    void variantsCoverTheStoredSpellingsForLookups() {
        assertEquals(List.of("+94771234567", "0771234567"), PhoneNumbers.variants("+94 77 123 4567"));
        assertEquals(List.of("0771234567", "+94771234567").stream().sorted().toList(),
                PhoneNumbers.variants("0771234567").stream().sorted().toList());
        assertEquals(List.of("garbage"), PhoneNumbers.variants("garbage"));
        assertTrue(PhoneNumbers.variants(null).isEmpty());
    }

    @Test
    void normalizeOrKeep_keepsInvalidInputSoValidationCanRejectIt() {
        assertEquals("+94771234567", PhoneNumbers.normalizeOrKeep("0771234567"));
        assertEquals("12345", PhoneNumbers.normalizeOrKeep(" 12345 "));
        assertNull(PhoneNumbers.normalizeOrKeep(null));
    }

    @Test
    void masking_neverShowsTheWholeNumber() {
        assertEquals("07*****567", PhoneNumbers.mask("0771234567"));
        assertEquals("+94*****567", PhoneNumbers.mask("+94771234567"));
        assertEquals("*****", PhoneNumbers.mask("12345"));
        assertEquals("<none>", PhoneNumbers.mask(null));
        assertFalse(PhoneNumbers.mask("0771234567").contains("1234"));
    }
}
