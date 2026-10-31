package com.suwasarana.api.common;

import org.springframework.http.HttpStatus;

/**
 * THE expected, client-facing failure of the whole API (bad input, conflict, unauthorized...). Every module throws
 * this (or a subclass such as {@code AuthException}); {@link GlobalExceptionHandler} renders it as the
 * {@link ApiResponse} envelope:
 * <ul>
 *   <li>{@code code} - machine-readable key ({@code area.reason}); the frontend translates it;</li>
 *   <li>{@code message} - human English fallback, safe to show; when the thrower gives none it is looked up in
 *       {@link ErrorMessages} by code;</li>
 *   <li>{@code data} - optional details: a {@code {field: code}} map for validation failures, or
 *       {@code {retryAfterSeconds: n}} (the {@code Retry-After} header is then sent as well).</li>
 * </ul>
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final transient Object data;
    private final Integer retryAfterSeconds;

    public ApiException(HttpStatus status, String message) {
        this(status, null, message);
    }

    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, null, null);
    }

    /** Full form; a {@code null} message is replaced by the English fallback of {@code code}. */
    protected ApiException(HttpStatus status, String code, String message, Object data, Integer retryAfterSeconds) {
        super(message != null ? message : ErrorMessages.english(code));
        this.status = status;
        this.code = code;
        this.data = data;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public HttpStatus getStatus() {
        return status;
    }

    /** Machine-readable error code ({@code area.reason}), or {@code null} when the thrower did not provide one. */
    public String getCode() {
        return code;
    }

    /** Optional details for the response {@code data} field (field-code map, ...). May be {@code null}. */
    public Object getData() {
        return data;
    }

    /** When set, the response also carries a {@code Retry-After} header. */
    public Integer getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    public static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }

    public static ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    public static ApiException unauthorized(String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, message);
    }

    public static ApiException unauthorized(String code, String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, code, message);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, message);
    }

    public static ApiException forbidden(String code, String message) {
        return new ApiException(HttpStatus.FORBIDDEN, code, message);
    }

    public static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, message);
    }

    public static ApiException notFound(String code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message);
    }

    public static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, message);
    }

    public static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    public static ApiException tooManyRequests(String message) {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, message);
    }

    public static ApiException tooManyRequests(String code, String message) {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, code, message);
    }

    public static ApiException unprocessable(String message) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, message);
    }

    public static ApiException unprocessable(String code, String message) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
    }

    public static ApiException gone(String message) {
        return new ApiException(HttpStatus.GONE, message);
    }

    public static ApiException gone(String code, String message) {
        return new ApiException(HttpStatus.GONE, code, message);
    }
}
