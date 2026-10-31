package com.suwasarana.api.ai.poster;

import org.springframework.http.HttpStatus;

/** A refused poster request with a stable machine code (for example poster.unverified_requester). */
public class PosterException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public PosterException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
}
