package com.suwasarana.api.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Works out the real client address of a request.
 *
 * <p>The {@code X-Forwarded-For} header is attacker-controlled unless it was written by our own reverse proxy,
 * so it is only believed when the <em>immediate peer</em> ({@link HttpServletRequest#getRemoteAddr()}) is inside
 * {@code app.rate-limit.trusted-proxies}. With an empty list (the default) the TCP peer address is used as is.
 * When the peer is trusted the header is walked from right to left, skipping further trusted proxies; the first
 * address that is not a trusted proxy is the client. Entries that are not IP literals are never resolved
 * (no DNS) and cut the walk short.
 *
 * <p>Deployment note: this class does its own header handling and looks through request wrappers, so it is not
 * affected by Spring's {@code ForwardedHeaderFilter} ({@code server.forward-headers-strategy=framework}). That
 * strategy is nevertheless unsafe for anything else that calls {@code request.getRemoteAddr()} (Spring 7 rewrites it
 * from the client-supplied header without any trusted-proxy check); prefer {@code none} or {@code native} (Tomcat
 * RemoteIpValve with {@code server.tomcat.remoteip.internal-proxies}) in {@code application.yml}. With {@code native}
 * the remote address is already the client and the trusted list may stay empty.
 */
@Component
public class ClientIpResolver {

    private static final Logger log = LoggerFactory.getLogger(ClientIpResolver.class);
    private static final String XFF = "X-Forwarded-For";

    private final List<IpCidr> trusted;

    @Autowired
    public ClientIpResolver(RateLimitProperties properties) {
        this(properties.getTrustedProxies());
    }

    public ClientIpResolver(List<String> trustedProxies) {
        List<IpCidr> parsed = new ArrayList<>();
        if (trustedProxies != null) {
            for (String raw : trustedProxies) {
                if (raw == null || raw.isBlank()) {
                    continue;
                }
                try {
                    parsed.add(IpCidr.parse(raw));
                } catch (IllegalArgumentException e) {
                    // Fail closed: an unparsable entry trusts nothing. Never fail startup over a typo in a hint list.
                    log.error("Ignoring invalid app.rate-limit.trusted-proxies entry '{}': {}", raw, e.getMessage());
                }
            }
        }
        this.trusted = Collections.unmodifiableList(parsed);
    }

    /** The client IP, in canonical text form ({@code unknown} when the container reports nothing). */
    public String resolve(HttpServletRequest original) {
        HttpServletRequest request = unwrap(original);
        String peer = request.getRemoteAddr();
        InetAddress peerAddr = IpCidr.parseLiteral(peer);
        if (peerAddr == null) {
            return peer == null || peer.isBlank() ? "unknown" : peer;
        }
        if (trusted.isEmpty() || !isTrusted(peerAddr)) {
            return canonical(peerAddr);
        }

        StringBuilder header = new StringBuilder();
        java.util.Enumeration<String> values = request.getHeaders(XFF);
        while (values != null && values.hasMoreElements()) {
            if (header.length() > 0) {
                header.append(',');
            }
            header.append(values.nextElement());
        }
        if (header.length() == 0) {
            return canonical(peerAddr);
        }

        String[] hops = header.toString().split(",");
        InetAddress current = peerAddr; // last hop known to be good
        for (int i = hops.length - 1; i >= 0; i--) {
            InetAddress hop = IpCidr.parseLiteral(stripPort(hops[i].trim()));
            if (hop == null) {
                return canonical(current); // garbage: do not guess
            }
            current = hop;
            if (!isTrusted(hop)) {
                return canonical(hop);
            }
        }
        return canonical(current); // every hop was one of our own proxies
    }

    /**
     * Returns the container's own request object. Spring 7's {@code ForwardedHeaderFilter}
     * ({@code server.forward-headers-strategy=framework}) replaces {@code getRemoteAddr()} with whatever the client
     * put in {@code Forwarded} / {@code X-Forwarded-For} - with no trusted-proxy check - and hides those headers.
     * Looking through such wrappers gives us the real TCP peer and the untouched header.
     */
    static HttpServletRequest unwrap(HttpServletRequest request) {
        HttpServletRequest current = request;
        for (int depth = 0; depth < 32; depth++) {
            if (current instanceof jakarta.servlet.http.HttpServletRequestWrapper wrapper
                    && wrapper.getRequest() instanceof HttpServletRequest inner) {
                current = inner;
            } else {
                break;
            }
        }
        return current;
    }

    private boolean isTrusted(InetAddress address) {
        for (IpCidr cidr : trusted) {
            if (cidr.contains(address)) {
                return true;
            }
        }
        return false;
    }

    private static String stripPort(String hop) {
        if (hop.startsWith("[")) {
            int end = hop.indexOf(']');
            return end > 0 ? hop.substring(0, end + 1) : hop;
        }
        int firstColon = hop.indexOf(':');
        if (firstColon > 0 && firstColon == hop.lastIndexOf(':')) { // "1.2.3.4:5678" (not IPv6)
            return hop.substring(0, firstColon);
        }
        return hop;
    }

    /**
     * IPv4 as dotted quad; IPv6 reduced to its /64 network, because a single subscriber is handed a whole /64
     * and would otherwise get unlimited fresh buckets.
     */
    static String canonical(InetAddress address) {
        if (address instanceof Inet6Address) {
            byte[] b = address.getAddress();
            StringBuilder sb = new StringBuilder("v6:");
            for (int i = 0; i < 8; i++) {
                sb.append(String.format("%02x", b[i] & 0xFF));
                if (i % 2 == 1 && i < 7) {
                    sb.append(':');
                }
            }
            return sb.toString();
        }
        return address.getHostAddress();
    }
}
