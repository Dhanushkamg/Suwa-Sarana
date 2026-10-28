package com.suwasarana.api.circle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CircleTokensTest {

    private final CircleTokens tokens = new CircleTokens("k1", "jwt");

    @Test
    @DisplayName("Tokens are 64 hex characters (256 bit) and never repeat")
    void generate() {
        String a = tokens.generate();
        assertTrue(a.matches("^[0-9a-f]{64}$"));
        assertNotEquals(a, tokens.generate());
    }

    @Test
    @DisplayName("The hash is stable, 64 hex characters and does not reveal the token")
    void hash() {
        String t = tokens.generate();
        assertEquals(tokens.hash(t), tokens.hash(t));
        assertTrue(tokens.hash(t).matches("^[0-9a-f]{64}$"));
        assertFalse(tokens.hash(t).contains(t.substring(0, 16)));
        assertNull(tokens.hash(null));
        assertNull(tokens.hash("  "));
        // the same value V40 computes in SQL: encode(sha256(convert_to(t,'UTF8')),'hex')
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", tokens.hash("abc"));
    }

    @Test
    @DisplayName("Encryption round-trips, is randomised, and refuses tampering and other keys")
    void encryption() {
        String t = tokens.generate();
        String e1 = tokens.encrypt(t);
        String e2 = tokens.encrypt(t);
        assertNotEquals(e1, e2);
        assertFalse(e1.contains(t));
        assertEquals(t, tokens.decrypt(e1).orElseThrow());

        String tampered = e1.substring(0, e1.length() - 4) + (e1.endsWith("AAAA") ? "BBBB" : "AAAA");
        assertTrue(tokens.decrypt(tampered).isEmpty());
        assertTrue(new CircleTokens("another-key", "jwt").decrypt(e1).isEmpty());
        assertTrue(tokens.decrypt(null).isEmpty());
        assertTrue(tokens.decrypt("plain-text").isEmpty());
        assertTrue(tokens.decrypt("v1:!!!not-base64").isEmpty());
    }

    @Test
    @DisplayName("Without an explicit key the JWT secret is used; with neither, startup fails")
    void keySource() {
        String t = "abc";
        assertEquals(t, new CircleTokens("", "jwt-secret").decrypt(new CircleTokens(null, "jwt-secret").encrypt(t)).orElseThrow());
        assertThrows(IllegalStateException.class, () -> new CircleTokens("", ""));
    }

    @Test
    @DisplayName("Phone normalisation")
    void phones() {
        assertEquals("+94771234567", PhoneNumbers.normalise("0771234567"));
        assertEquals("+94771234567", PhoneNumbers.normalise("+94 (77) 123-4567"));
        assertEquals("+94771234567", PhoneNumbers.normalise("94771234567"));
        assertEquals("+94771234567", PhoneNumbers.normalise("0094771234567"));
        assertEquals("+94771234567", PhoneNumbers.normalise("771234567"));
        assertEquals("+94112369931", PhoneNumbers.normalise("011 236 9931"));
        assertEquals("+447911123456", PhoneNumbers.normalise("+44 7911 123456"));
        for (String bad : new String[] {null, "", "abc", "12345", "0000000000", "0777777777", "+0771234567",
                "07712345678", "+94 77 123 456", "+1234567", "0771234567x", "++94771234567"}) {
            assertNull(PhoneNumbers.normalise(bad), String.valueOf(bad));
        }
    }
}
