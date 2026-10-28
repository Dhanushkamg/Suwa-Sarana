package com.suwasarana.api.ivr;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Verifies the {@code X-Twilio-Signature} header: base64(HMAC-SHA1(authToken, url + name1 + value1 + name2 + value2 ...)),
 * with the POST form parameters sorted by name. For GET requests the signed string is the full URL including the
 * query string and no parameters are appended. See https://www.twilio.com/docs/usage/security#validating-requests.
 */
@Component
public class TwilioSignatureValidator {

    private final IvrProperties properties;

    @Autowired
    public TwilioSignatureValidator(IvrProperties properties) {
        this.properties = properties;
    }

    /** True when the request carries a valid signature for the configured auth token. Fails closed without a token. */
    public boolean isValid(HttpServletRequest request, String signatureHeader) {
        String token = properties.getAuthToken();
        if (token == null || token.isBlank() || signatureHeader == null || signatureHeader.isBlank()) {
            return false;
        }
        String params = "POST".equalsIgnoreCase(request.getMethod()) ? signedParameters(request) : "";
        byte[] provided = signatureHeader.trim().getBytes(StandardCharsets.UTF_8);
        for (String url : candidateUrls(request)) {
            byte[] expected = sign(token, url + params).getBytes(StandardCharsets.UTF_8);
            if (MessageDigest.isEqual(expected, provided)) {
                return true;
            }
        }
        return false;
    }

    /** Public so tests (and tooling that wants to call the webhook) can create a valid signature. */
    public static String sign(String authToken, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(authToken.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            return Base64.getEncoder().encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA1 unavailable", e);
        }
    }

    /** name+value pairs of the form body, sorted by name; parameters that came from the query string are part of the URL. */
    private String signedParameters(HttpServletRequest request) {
        Set<String> queryNames = queryParameterNames(request.getQueryString());
        Map<String, String[]> all = request.getParameterMap();
        Map<String, String[]> sorted = new TreeMap<>();
        for (Map.Entry<String, String[]> e : all.entrySet()) {
            if (!queryNames.contains(e.getKey())) {
                sorted.put(e.getKey(), e.getValue());
            }
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String[]> e : sorted.entrySet()) {
            String[] values = e.getValue().clone();
            Arrays.sort(values);
            for (String v : values) {
                sb.append(e.getKey()).append(v);
            }
        }
        return sb.toString();
    }

    private static Set<String> queryParameterNames(String query) {
        Set<String> names = new LinkedHashSet<>();
        if (query == null || query.isEmpty()) {
            return names;
        }
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            String name = eq >= 0 ? pair.substring(0, eq) : pair;
            names.add(java.net.URLDecoder.decode(name, StandardCharsets.UTF_8));
        }
        return names;
    }

    /** The URL Twilio signed: the configured public URL when set, else the one seen by the application; with and without the default port. */
    List<String> candidateUrls(HttpServletRequest request) {
        String query = request.getQueryString();
        String base = properties.getPublicUrl();
        if (base == null || base.isBlank()) {
            StringBuffer url = request.getRequestURL();
            base = url == null ? "" : url.toString();
        }
        int q = base.indexOf('?');
        if (q >= 0) {
            base = base.substring(0, q);
        }
        String full = query == null || query.isEmpty() ? base : base + "?" + query;

        List<String> out = new ArrayList<>();
        out.add(full);
        String alternative = withToggledDefaultPort(full);
        if (alternative != null) {
            out.add(alternative);
        }
        return out;
    }

    /** {@code https://h/p} <-> {@code https://h:443/p}; null when the port is not a default one. */
    static String withToggledDefaultPort(String url) {
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            if (scheme == null || uri.getHost() == null) {
                return null;
            }
            int defaultPort = "https".equalsIgnoreCase(scheme) ? 443 : "http".equalsIgnoreCase(scheme) ? 80 : -1;
            if (defaultPort < 0) {
                return null;
            }
            String authorityWithoutPort = uri.getHost();
            if (uri.getUserInfo() != null) {
                authorityWithoutPort = uri.getUserInfo() + "@" + authorityWithoutPort;
            }
            String rest = url.substring(url.indexOf("//") + 2 + (uri.getRawAuthority() == null ? 0 : uri.getRawAuthority().length()));
            if (uri.getPort() == -1) {
                return scheme + "://" + authorityWithoutPort + ":" + defaultPort + rest;
            }
            if (uri.getPort() == defaultPort) {
                return scheme + "://" + authorityWithoutPort + rest;
            }
            return null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
