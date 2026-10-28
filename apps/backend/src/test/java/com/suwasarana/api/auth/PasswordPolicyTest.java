package com.suwasarana.api.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class PasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy();

    private String violation(String pw) {
        return policy.violation(pw, "nimal.perera@example.com", "0771234567");
    }

    @Test
    void lengthBounds() {
        assertEquals(AuthCodes.PASSWORD_REQUIRED, violation(null));
        assertEquals(AuthCodes.PASSWORD_REQUIRED, violation("   "));
        assertEquals(AuthCodes.PASSWORD_TOO_SHORT, violation("Abc12345!"));       // 9
        assertNull(violation("correct-horse-battery"));
        assertEquals(AuthCodes.PASSWORD_TOO_LONG, violation("x".repeat(129) + "yQ7!"));
        assertNull(violation("a1B!".repeat(32)));                                  // exactly 128
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "password123", "Password123", "PASSWORD1234", "qwertyuiop", "1234567890", "iloveyou123",
            "p@ssw0rd1234", "Password2024!", "admin12345", "welcome123", "letmein1234", "suwasarana123",
            "9876543210", "abcdefghij", "aaaaaaaaaaaa", "abababababab", "0123456789"})
    void commonAndTrivialPasswordsAreRefused(String pw) {
        assertEquals(AuthCodes.PASSWORD_COMMON, violation(pw), pw);
    }

    @ParameterizedTest
    @ValueSource(strings = {"correct-horse-battery", "Tr0ub4dor&3-Colombo", "blue-kandy-train-7421", "xK9#mQ2$vL8!pR"})
    void reasonablePasswordsAreAccepted(String pw) {
        assertNull(violation(pw), pw);
    }

    @Test
    void notTheAccountsOwnEmailEmailNameOrPhone() {
        assertEquals(AuthCodes.PASSWORD_SAME_AS_IDENTIFIER, policy.violation("nimal.perera@example.com", "nimal.perera@example.com", null));
        assertEquals(AuthCodes.PASSWORD_SAME_AS_IDENTIFIER, policy.violation("Nimal.Perera@Example.com", "nimal.perera@example.com", null));
        assertEquals(AuthCodes.PASSWORD_SAME_AS_IDENTIFIER, policy.violation("nimal.perera", "nimal.perera@example.com", null));
        assertEquals(AuthCodes.PASSWORD_SAME_AS_IDENTIFIER, policy.violation("0771234567", null, "+94771234567"));
        assertEquals(AuthCodes.PASSWORD_SAME_AS_IDENTIFIER, policy.violation("+94771234567", null, "0771234567"));
        assertEquals(AuthCodes.PASSWORD_SAME_AS_IDENTIFIER, policy.violation("94771234567", null, "0771234567"));
    }

    @Test
    void enforce_throwsA400WithTheFieldCode() {
        AuthException ex = assertThrows(AuthException.class, () -> policy.enforce("password", "short", null, null));
        assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals(AuthCodes.PASSWORD_TOO_SHORT, ex.getFieldErrors().get("password"));
    }
}
