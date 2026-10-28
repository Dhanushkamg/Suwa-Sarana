package com.suwasarana.api.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientIpResolverTest {

    private static MockHttpServletRequest request(String remote, String... xff) {
        MockHttpServletRequest r = new MockHttpServletRequest();
        r.setRemoteAddr(remote);
        for (String v : xff) {
            r.addHeader("X-Forwarded-For", v);
        }
        return r;
    }

    @Test
    @DisplayName("No trusted proxies configured: X-Forwarded-For is never believed")
    void emptyTrustedList_usesRemoteAddress() {
        ClientIpResolver resolver = new ClientIpResolver(List.of());
        assertEquals("203.0.113.9", resolver.resolve(request("203.0.113.9", "1.1.1.1")));
    }

    @Test
    @DisplayName("Spoofed X-Forwarded-For from an untrusted peer is ignored")
    void spoofedHeaderFromUntrustedPeer_isIgnored() {
        ClientIpResolver resolver = new ClientIpResolver(List.of("10.0.0.0/8"));
        assertEquals("198.51.100.7", resolver.resolve(request("198.51.100.7", "9.9.9.9, 8.8.8.8")));
    }

    @Test
    @DisplayName("Trusted proxy: the client is the right-most address that is not a trusted proxy")
    void trustedPeer_usesRightmostUntrustedHop() {
        ClientIpResolver resolver = new ClientIpResolver(List.of("10.0.0.0/8", "172.16.0.0/12"));
        // attacker prepends 6.6.6.6; the load balancer appended the address it really saw (203.0.113.50)
        assertEquals("203.0.113.50",
                resolver.resolve(request("10.1.2.3", "6.6.6.6, 203.0.113.50, 172.16.5.5")));
    }

    @Test
    @DisplayName("Multiple X-Forwarded-For header lines are combined in order")
    void multipleHeaderLines() {
        ClientIpResolver resolver = new ClientIpResolver(List.of("10.0.0.0/8"));
        assertEquals("203.0.113.50", resolver.resolve(request("10.0.0.1", "6.6.6.6", "203.0.113.50")));
    }

    @Test
    @DisplayName("Trusted peer without the header: the peer itself")
    void trustedPeerNoHeader() {
        ClientIpResolver resolver = new ClientIpResolver(List.of("10.0.0.0/8"));
        assertEquals("10.0.0.1", resolver.resolve(request("10.0.0.1")));
    }

    @Test
    @DisplayName("Garbage or host names in the header are never resolved or believed")
    void garbageEntry_fallsBackToLastTrustedHop() {
        ClientIpResolver resolver = new ClientIpResolver(List.of("10.0.0.0/8"));
        assertEquals("10.0.0.1", resolver.resolve(request("10.0.0.1", "evil.example.com")));
        assertEquals("10.0.0.1", resolver.resolve(request("10.0.0.1", "unknown")));
        assertEquals("10.0.0.1", resolver.resolve(request("10.0.0.1", "300.1.1.1")));
    }

    @Test
    @DisplayName("Ports and brackets are stripped from header entries")
    void portsAreStripped() {
        ClientIpResolver resolver = new ClientIpResolver(List.of("10.0.0.0/8"));
        assertEquals("203.0.113.5", resolver.resolve(request("10.0.0.1", "203.0.113.5:51234")));
    }

    @Test
    @DisplayName("All hops trusted: falls back to the left-most (original) hop")
    void everyHopTrusted() {
        ClientIpResolver resolver = new ClientIpResolver(List.of("10.0.0.0/8"));
        assertEquals("10.9.9.9", resolver.resolve(request("10.0.0.1", "10.9.9.9, 10.0.0.2")));
    }

    @Test
    @DisplayName("IPv6 clients are grouped by /64 so one subscriber cannot mint unlimited buckets")
    void ipv6IsGroupedBySlash64() {
        ClientIpResolver resolver = new ClientIpResolver(List.of());
        String a = resolver.resolve(request("2001:db8:1:2:aaaa:bbbb:cccc:dddd"));
        String b = resolver.resolve(request("2001:db8:1:2:1111:2222:3333:4444"));
        String c = resolver.resolve(request("2001:db8:1:3::1"));
        assertEquals(a, b);
        assertFalse(a.equals(c));
    }

    @Test
    @DisplayName("An invalid trusted-proxy entry trusts nothing and does not break startup")
    void invalidTrustedEntry_failsClosed() {
        ClientIpResolver resolver = new ClientIpResolver(List.of("not-a-cidr", "10.0.0.0/99"));
        assertEquals("10.0.0.1", resolver.resolve(request("10.0.0.1", "203.0.113.5")));
    }

    @Test
    @DisplayName("A ForwardedHeaderFilter-style wrapper that rewrites getRemoteAddr() from the client header is looked through")
    void forwardedHeaderWrapperIsLookedThrough() {
        MockHttpServletRequest real = request("198.51.100.7", "9.9.9.9");
        jakarta.servlet.http.HttpServletRequest spoofed = new jakarta.servlet.http.HttpServletRequestWrapper(real) {
            @Override public String getRemoteAddr() { return "9.9.9.9"; }
            @Override public String getHeader(String name) { return null; }
            @Override public java.util.Enumeration<String> getHeaders(String name) { return java.util.Collections.emptyEnumeration(); }
        };
        assertEquals("198.51.100.7", new ClientIpResolver(List.of("10.0.0.0/8")).resolve(spoofed));
    }

    @Test
    @DisplayName("CIDR matching: prefix lengths, single addresses and IPv6")
    void cidrMatching() {
        assertTrue(IpCidr.parse("192.168.1.0/24").contains("192.168.1.77"));
        assertFalse(IpCidr.parse("192.168.1.0/24").contains("192.168.2.1"));
        assertTrue(IpCidr.parse("203.0.113.9").contains("203.0.113.9"));
        assertFalse(IpCidr.parse("203.0.113.9").contains("203.0.113.10"));
        assertTrue(IpCidr.parse("10.0.0.0/13").contains("10.7.255.255"));
        assertFalse(IpCidr.parse("10.0.0.0/13").contains("10.8.0.0"));
        assertTrue(IpCidr.parse("2001:db8::/32").contains("2001:db8:ffff::1"));
        assertFalse(IpCidr.parse("2001:db8::/32").contains("2001:db9::1"));
        assertTrue(IpCidr.parse("0.0.0.0/0").contains("1.2.3.4"));
        assertThrows(IllegalArgumentException.class, () -> IpCidr.parse("10.0.0.0/33"));
        assertThrows(IllegalArgumentException.class, () -> IpCidr.parse("example.com"));
    }
}
