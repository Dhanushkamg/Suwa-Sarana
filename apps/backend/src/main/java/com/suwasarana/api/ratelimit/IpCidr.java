package com.suwasarana.api.ratelimit;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.regex.Pattern;

/** An IPv4/IPv6 network in CIDR notation ({@code 10.0.0.0/8}, {@code ::1/128}, or a single address). */
public final class IpCidr {

    private static final Pattern IPV4 = Pattern.compile("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$");
    private static final Pattern IPV6_CHARS = Pattern.compile("^[0-9a-fA-F:.]+$");

    private final byte[] network;
    private final int prefixBits;

    private IpCidr(byte[] network, int prefixBits) {
        this.network = network;
        this.prefixBits = prefixBits;
    }

    /** Parses {@code a.b.c.d[/n]} or an IPv6 equivalent. Throws {@link IllegalArgumentException} when invalid. */
    public static IpCidr parse(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("empty CIDR");
        }
        String t = text.trim();
        String addr = t;
        Integer prefix = null;
        int slash = t.indexOf('/');
        if (slash >= 0) {
            addr = t.substring(0, slash);
            try {
                prefix = Integer.parseInt(t.substring(slash + 1));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("invalid CIDR prefix: " + text);
            }
        }
        InetAddress inet = parseLiteral(addr);
        if (inet == null) {
            throw new IllegalArgumentException("invalid IP address in CIDR: " + text);
        }
        byte[] bytes = inet.getAddress();
        int max = bytes.length * 8;
        int bits = prefix == null ? max : prefix;
        if (bits < 0 || bits > max) {
            throw new IllegalArgumentException("CIDR prefix out of range: " + text);
        }
        return new IpCidr(mask(bytes, bits), bits);
    }

    /** True when the address (an IP literal, never a host name) lies inside this network. */
    public boolean contains(String ipLiteral) {
        InetAddress inet = parseLiteral(ipLiteral);
        return inet != null && contains(inet);
    }

    public boolean contains(InetAddress address) {
        byte[] bytes = address.getAddress();
        return bytes.length == network.length && Arrays.equals(mask(bytes, prefixBits), network);
    }

    private static byte[] mask(byte[] bytes, int bits) {
        byte[] out = new byte[bytes.length];
        int full = bits / 8;
        int rem = bits % 8;
        for (int i = 0; i < out.length; i++) {
            if (i < full) {
                out[i] = bytes[i];
            } else if (i == full && rem > 0) {
                out[i] = (byte) (bytes[i] & (0xFF << (8 - rem)));
            }
        }
        return out;
    }

    /**
     * Parses an IP literal without ever touching DNS. Returns {@code null} for anything that is not a
     * literal address (host names, "unknown", garbage).
     */
    public static InetAddress parseLiteral(String text) {
        if (text == null) {
            return null;
        }
        String s = text.trim();
        if (s.startsWith("[") && s.endsWith("]")) {
            s = s.substring(1, s.length() - 1);
        }
        int zone = s.indexOf('%');
        if (zone >= 0) {
            s = s.substring(0, zone);
        }
        try {
            if (s.indexOf(':') >= 0) {
                if (!IPV6_CHARS.matcher(s).matches()) {
                    return null;
                }
                return InetAddress.getByName(s); // contains ':' => parsed as a literal, no lookup
            }
            java.util.regex.Matcher m = IPV4.matcher(s);
            if (!m.matches()) {
                return null;
            }
            byte[] b = new byte[4];
            for (int i = 0; i < 4; i++) {
                int v = Integer.parseInt(m.group(i + 1));
                if (v > 255) {
                    return null;
                }
                b[i] = (byte) v;
            }
            return InetAddress.getByAddress(b);
        } catch (UnknownHostException | NumberFormatException e) {
            return null;
        }
    }

    @Override
    public String toString() {
        return "IpCidr/" + prefixBits;
    }
}
