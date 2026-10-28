package com.suwasarana.api.auth;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Renders {@link AuthException} as {@code {success:false, message:<code>, code:<code>, data:{field:code}|{retryAfterSeconds}, timestamp}}.
 * Runs before the shared handler so the explicit {@code code} (and Retry-After) are available today,
 * independent of when the shared error envelope grows a {@code code} field.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuthExceptionHandler {

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<Map<String, Object>> handle(AuthException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("message", ex.getCode());
        body.put("code", ex.getCode());

        Map<String, Object> data = new LinkedHashMap<>(ex.getFieldErrors());
        if (ex.getRetryAfterSeconds() != null) {
            data.put("retryAfterSeconds", ex.getRetryAfterSeconds());
        }
        body.put("data", data.isEmpty() ? null : data);
        body.put("timestamp", LocalDateTime.now());

        ResponseEntity.BodyBuilder builder = ResponseEntity.status(ex.getStatus());
        if (ex.getRetryAfterSeconds() != null) {
            builder.header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()));
        }
        return builder.body(body);
    }
}
