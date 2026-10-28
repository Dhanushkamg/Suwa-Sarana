package com.suwasarana.api.auth;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.*;

class OriginGuardTest {

    private final OriginGuard guard = new OriginGuard("http://localhost:3000, https://app.suwasarana.lk/");

    @Test
    void anOriginFromTheAllowList_isAccepted_trailingSlashesIgnored() {
        assertTrue(guard.isAllowed("http://localhost:3000", null));
        assertTrue(guard.isAllowed("https://app.suwasarana.lk", null));
        assertTrue(guard.isAllowed("HTTPS://APP.SUWASARANA.LK/", null));
    }

    @Test
    void aForeignOrigin_isRefused_evenIfFetchMetadataLooksFriendly() {
        assertFalse(guard.isAllowed("https://evil.example", null));
        assertFalse(guard.isAllowed("https://evil.example", "same-site"));
        assertFalse(guard.isAllowed("null", null));
        assertFalse(guard.isAllowed("http://localhost:3000.evil.example", null));
    }

    @Test
    void withoutOrigin_secFetchSiteDecides() {
        assertTrue(guard.isAllowed(null, "same-origin"));
        assertTrue(guard.isAllowed(null, "same-site"));
        assertTrue(guard.isAllowed(null, "none"));
        assertFalse(guard.isAllowed(null, "cross-site"));
    }

    @Test
    void nonBrowserClients_withNeitherHeader_areAllowed() {
        assertTrue(guard.isAllowed(null, null));
        assertTrue(guard.isAllowed("", ""));
    }

    @Test
    void check_throwsA403WithTheCode() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/refresh");
        request.addHeader("Origin", "https://evil.example");

        AuthException ex = assertThrows(AuthException.class, () -> guard.check(request));

        assertEquals(403, ex.getStatus().value());
        assertEquals(AuthCodes.ORIGIN_BLOCKED, ex.getCode());

        MockHttpServletRequest ok = new MockHttpServletRequest("POST", "/api/auth/refresh");
        ok.addHeader("Origin", "http://localhost:3000");
        assertDoesNotThrow(() -> guard.check(ok));
    }
}
