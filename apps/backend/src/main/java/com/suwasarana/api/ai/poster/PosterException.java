package com.suwasarana.api.ai.poster;

import com.suwasarana.api.common.ApiException;
import org.springframework.http.HttpStatus;

/**
 * A refused poster request with a stable machine code (for example {@code poster.unverified_requester}). It is an
 * {@link ApiException}, so {@code GlobalExceptionHandler} renders the standard error envelope
 * ({@code success=false, message, code, requestId, timestamp}) with {@code code} at the top level.
 */
public class PosterException extends ApiException {

    public PosterException(HttpStatus status, String code, String message) {
        super(status, code, message);
    }
}
