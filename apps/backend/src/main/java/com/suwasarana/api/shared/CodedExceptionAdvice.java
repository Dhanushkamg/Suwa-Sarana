package com.suwasarana.api.shared;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Renders {@link CodedApiException} with the same envelope as ApiResponse plus a "code" field.
 * Runs before the general handler (which only knows ApiException) so the code is not lost.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CodedExceptionAdvice {

    @ExceptionHandler(CodedApiException.class)
    public ResponseEntity<Map<String, Object>> handle(CodedApiException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("message", ex.getMessage());
        body.put("code", ex.getCode());
        body.put("data", null);
        body.put("timestamp", LocalDateTime.now());
        return ResponseEntity.status(ex.getStatus()).body(body);
    }
}
