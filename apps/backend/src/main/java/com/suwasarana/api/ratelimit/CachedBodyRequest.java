package com.suwasarana.api.ratelimit;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.SequenceInputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Lets the rate limiter peek at the start of a request body and still hand the complete, unread body to the
 * controller: the already-read prefix is replayed in front of the rest of the original stream.
 */
final class CachedBodyRequest extends HttpServletRequestWrapper {

    private final byte[] prefix;
    private final InputStream rest;

    CachedBodyRequest(HttpServletRequest request, byte[] prefix, InputStream rest) {
        super(request);
        this.prefix = prefix;
        this.rest = rest;
    }

    /** Reads up to {@code max + 1} bytes; the caller can tell from the length whether the body is larger than {@code max}. */
    static byte[] peek(InputStream in, int max) throws IOException {
        return in.readNBytes(max + 1);
    }

    @Override
    public ServletInputStream getInputStream() {
        InputStream joined = new SequenceInputStream(new ByteArrayInputStream(prefix), rest);
        return new ServletInputStream() {
            @Override public int read() throws IOException { return joined.read(); }
            @Override public int read(byte[] b, int off, int len) throws IOException { return joined.read(b, off, len); }
            @Override public boolean isFinished() { return false; }
            @Override public boolean isReady() { return true; }
            @Override public void setReadListener(ReadListener readListener) {
                throw new UnsupportedOperationException("async reads are not supported on a replayed body");
            }
        };
    }

    @Override
    public BufferedReader getReader() {
        String enc = getCharacterEncoding();
        Charset cs = enc != null ? Charset.forName(enc) : StandardCharsets.UTF_8;
        return new BufferedReader(new InputStreamReader(getInputStream(), cs));
    }
}
