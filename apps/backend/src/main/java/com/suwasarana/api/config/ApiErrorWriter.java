package com.suwasarana.api.config;

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

    public static void write(HttpServletResponse response, HttpStatus status, String code, String message) throws IOException {
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
                .append("\",\"data\":null,\"timestamp\":\"").append(Instant.now()).append('"');
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
