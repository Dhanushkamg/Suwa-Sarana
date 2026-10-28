package com.suwasarana.api.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.slf4j.MDC;

import java.time.Instant;

/**
 * The single JSON envelope of every API response.
 *
 * <pre>
 * { "success": false, "message": "Validation failed", "code": "validation.failed",
 *   "data": { "email": "must be a well-formed email address" },
 *   "timestamp": "2026-10-28T09:30:00.123Z", "requestId": "4f1c..." }
 * </pre>
 *
 * {@code code} (machine-readable, localisable) and {@code requestId} (support correlation, errors only) are omitted
 * when absent, so existing successful responses are unchanged. {@code timestamp} is an absolute UTC instant
 * serialised as an ISO-8601 string.
 */
@JsonPropertyOrder({"success", "message", "code", "data", "timestamp", "requestId"})
public class ApiResponse<T> {
    private boolean success;
    private String message;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String code;
    private T data;
    private Instant timestamp;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String requestId;

    public ApiResponse() {}

    public ApiResponse(boolean success, String message, T data) {
        this(success, message, null, data);
    }

    public ApiResponse(boolean success, String message, String code, T data) {
        this.success = success;
        this.message = message;
        this.code = code;
        this.data = data;
        this.timestamp = Instant.now();
        if (!success) {
            this.requestId = MDC.get(RequestContext.MDC_REQUEST_ID);
        }
    }

    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(true, message, data);
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, "Success", data);
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, message, null);
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(false, message, code, null);
    }

    public static <T> ApiResponse<T> error(String code, String message, T data) {
        return new ApiResponse<>(false, message, code, data);
    }

    // Getters and Setters
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public T getData() { return data; }
    public void setData(T data) { this.data = data; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
