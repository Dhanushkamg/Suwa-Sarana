package com.suwasarana.api.config;

import com.suwasarana.api.common.ErrorCodes;
import com.suwasarana.api.common.ErrorMessages;
import com.suwasarana.api.common.RequestContext;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Writes the {@code ApiResponse} error envelope from servlet-filter level code (Spring Security entry points, filters),
 * where {@code GlobalExceptionHandler} does not run. Only constant, developer-written texts are accepted, so no
 * escaping of user input is needed beyond the JSON string escaping done here.
 */
public final class ApiErrorWriter {

    private ApiErrorWriter() {}

    /**
     * 429 for the rate limiters: code {@code rate.limited}, {@code Retry-After} header and {@code data.retryAfterSeconds}
     * (the same shape {@code AuthException.tooManyRequests} produces for account lockouts).
     */
    public static void writeTooManyRequests(HttpServletResponse response, long retryAfterSeconds) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        long retry = Math.max(1, retryAfterSeconds);
        response.setHeader("Retry-After", Long.toString(retry));
        response.setHeader("Cache-Control", "no-store");
        write(response, HttpStatus.TOO_MANY_REQUESTS, ErrorCodes.RATE_LIMITED, ErrorMessages.english(ErrorCodes.RATE_LIMITED),
                "{\"retryAfterSeconds\":" + retry + "}");
    }

    public static void write(HttpServletResponse response, HttpStatus status, String code, String message) throws IOException {
        write(response, status, code, message, null);
    }

    /** {@code dataJson}: an already-valid JSON value built from constants and numbers only, or {@code null}. */
    private static void write(HttpServletResponse response, HttpStatus status, String code, String message, String dataJson) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String requestId = MDC.get(RequestContext.MDC_REQUEST_ID);
        StringBuilder json = new StringBuilder(160)
                .append("{\"success\":false,\"message\":\"").append(escape(message))
                .append("\",\"code\":\"").append(escape(code))
                .append("\",\"data\":").append(dataJson == null ? "null" : dataJson).append(",\"timestamp\":\"").append(Instant.now()).append('"');
        if (requestId != null) {
            json.append(",\"requestId\":\"").append(escape(requestId)).append('"');
        }
        json.append('}');
        response.getWriter().write(json.toString());
    }

    private static String escape(String value) {
        StringBuilder sb = new StringBuilder(value.length());
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
