package com.suwasarana.api.auth;

import com.suwasarana.api.common.ApiException;
import org.springframework.http.HttpStatus;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An expected failure in the authentication / identity / admin / privacy APIs. Carries a machine-readable
 * {@code code} (see {@link AuthCodes}), optional per-field codes and an optional retry delay.
 * The human-readable {@code message} is the code itself: the frontend translates codes.
 */
public class AuthException extends ApiException {

    private final String code;
    private final Map<String, String> fieldErrors;
    private final Integer retryAfterSeconds;

    public AuthException(HttpStatus status, String code) {
        this(status, code, Collections.emptyMap(), null);
    }

    public AuthException(HttpStatus status, String code, Map<String, String> fieldErrors, Integer retryAfterSeconds) {
        // One-line wiring point: switch to super(status, code, code) once ApiException has an explicit code.
        super(status, code);
        this.code = code;
        this.fieldErrors = fieldErrors == null ? Collections.emptyMap() : Collections.unmodifiableMap(new LinkedHashMap<>(fieldErrors));
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public String getCode() {
        return code;
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }

    public Integer getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    // ---- factories ----------------------------------------------------------------------------------------

    public static AuthException of(HttpStatus status, String code) {
        return new AuthException(status, code);
    }

    public static AuthException badRequest(String code) {
        return new AuthException(HttpStatus.BAD_REQUEST, code);
    }

    public static AuthException unauthorized(String code) {
        return new AuthException(HttpStatus.UNAUTHORIZED, code);
    }

    public static AuthException forbidden(String code) {
        return new AuthException(HttpStatus.FORBIDDEN, code);
    }

    public static AuthException notFound(String code) {
        return new AuthException(HttpStatus.NOT_FOUND, code);
    }

    public static AuthException conflict(String code) {
        return new AuthException(HttpStatus.CONFLICT, code);
    }

    /** 400 with one field-level code: {@code data: {field: code}}. */
    public static AuthException invalidField(String field, String code) {
        return new AuthException(HttpStatus.BAD_REQUEST, AuthCodes.VALIDATION_FAILED, Map.of(field, code), null);
    }

    public static AuthException invalidFields(Map<String, String> fields) {
        return new AuthException(HttpStatus.BAD_REQUEST, AuthCodes.VALIDATION_FAILED, fields, null);
    }

    /** 429 with a Retry-After. */
    public static AuthException tooManyRequests(String code, int retryAfterSeconds) {
        return new AuthException(HttpStatus.TOO_MANY_REQUESTS, code, Collections.emptyMap(), Math.max(1, retryAfterSeconds));
    }
}
