package com.suwasarana.api.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CachedBodyRequestFactoryTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private static MockHttpServletRequest json(String body) {
        MockHttpServletRequest r = new MockHttpServletRequest("POST", "/api/auth/login");
        r.setContentType("application/json");
        r.setContent(body.getBytes(StandardCharsets.UTF_8));
        return r;
    }

    @Test
    @DisplayName("The identifier is read and the body is still fully readable afterwards")
    void bodyIsReplayed() throws Exception {
        String body = "{\"email\":\"A@B.com\",\"password\":\"secret\"}";
        CachedBodyRequestFactory.Peeked p = CachedBodyRequestFactory.peekJson(json(body), 1024, mapper);
        assertEquals("e:a@b.com", p.identifier());
        assertEquals(body, new String(p.request().getInputStream().readAllBytes(), StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("A body larger than the inspection limit is not parsed but is replayed completely")
    void oversizedBodyIsReplayedWithoutIdentifier() throws Exception {
        String body = "{\"email\":\"a@b.com\",\"pad\":\"" + "x".repeat(5000) + "\"}";
        MockHttpServletRequest raw = json(body);
        CachedBodyRequestFactory.Peeked p = CachedBodyRequestFactory.peekJson(raw, 1024, mapper);
        assertNull(p.identifier());
        assertEquals(body, new String(p.request().getInputStream().readAllBytes(), StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("Malformed JSON gives no identifier and the controller still receives the bytes")
    void malformedJsonIsReplayed() throws Exception {
        CachedBodyRequestFactory.Peeked p = CachedBodyRequestFactory.peekJson(json("{not json"), 1024, mapper);
        assertNull(p.identifier());
        assertEquals("{not json", new String(p.request().getInputStream().readAllBytes(), StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("Non-JSON requests are left untouched")
    void nonJsonUntouched() {
        MockHttpServletRequest r = new MockHttpServletRequest("POST", "/api/auth/login");
        r.setContentType("application/x-www-form-urlencoded");
        CachedBodyRequestFactory.Peeked p = CachedBodyRequestFactory.peekJson(r, 1024, mapper);
        assertNull(p.identifier());
        assertEquals(r, p.request());
    }
}
