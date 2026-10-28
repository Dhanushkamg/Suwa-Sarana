package com.suwasarana.api.ratelimit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.util.Locale;

/** Reads the account identifier out of a small JSON request body without consuming the body. */
public final class CachedBodyRequestFactory {

    /** The request to pass down the chain (possibly a replaying wrapper) and the identifier found, if any. */
    public record Peeked(HttpServletRequest request, String identifier) {
    }

    private CachedBodyRequestFactory() {
    }

    public static Peeked peekJson(HttpServletRequest request, int maxBytes, ObjectMapper mapper) {
        String contentType = request.getContentType();
        if (maxBytes <= 0 || contentType == null || !contentType.toLowerCase(Locale.ROOT).contains("json")) {
            return new Peeked(request, null);
        }
        long declared = request.getContentLengthLong();
        if (declared > maxBytes) {
            return new Peeked(request, null); // too big to inspect; the per-IP limit still applies
        }

        ServletInputStream in;
        byte[] prefix;
        try {
            in = request.getInputStream();
            prefix = CachedBodyRequest.peek(in, maxBytes);
        } catch (IOException e) {
            return new Peeked(request, null);
        }

        // From here on the original stream is (partly) consumed, so the wrapper must always be passed down.
        HttpServletRequest wrapped = new CachedBodyRequest(request, prefix, in);
        if (prefix.length > maxBytes) {
            return new Peeked(wrapped, null);
        }
        try {
            JsonNode root = mapper.readTree(prefix);
            return new Peeked(wrapped, Identifiers.fromJson(root));
        } catch (IOException | RuntimeException e) {
            // Malformed body: the controller will answer 400. Only the per-IP limit applies.
            return new Peeked(wrapped, null);
        }
    }
}
