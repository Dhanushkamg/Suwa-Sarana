package com.suwasarana.api.common;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Turns every failure into the {@link ApiResponse} envelope with the right HTTP status and a machine-readable
 * {@code code} (see {@link ErrorCodes}). Contract: {@code message} = human English fallback, {@code code} = machine key
 * the frontend translates, {@code data} = field-to-message map (Bean Validation: auth DTOs put a field code there, the
 * other DTOs an English sentence) or details such as {@code retryAfterSeconds}. Internal details (SQL, stack traces,
 * exception text of unexpected errors) are logged (class name + stack, never personal data) but never returned.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Field-level validation errors: 400, code {@code validation.failed}, {@code data} = {field: message}. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = error instanceof FieldError fe ? fe.getField() : error.getObjectName();
            errors.put(fieldName, error.getDefaultMessage());
        });
        return validationFailed(errors);
    }

    /** Validation of controller method parameters (@RequestParam / @PathVariable constraints). */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getParameterValidationResults().forEach(result -> {
            String name = result.getMethodParameter().getParameterName();
            String key = name != null ? name : "arg" + result.getMethodParameter().getParameterIndex();
            result.getResolvableErrors().forEach(error -> errors.putIfAbsent(key, error.getDefaultMessage()));
        });
        return validationFailed(errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> handleConstraintViolation(ConstraintViolationException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(v -> {
            String path = v.getPropertyPath().toString();
            String key = path.contains(".") ? path.substring(path.lastIndexOf('.') + 1) : path;
            errors.putIfAbsent(key, v.getMessage());
        });
        return validationFailed(errors);
    }

    private ResponseEntity<Object> validationFailed(Map<String, String> errors) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ErrorCodes.VALIDATION_FAILED, "Validation failed", errors));
    }

    /**
     * Every other Spring MVC exception (malformed JSON, unknown route, wrong method,
     * missing parameter...) keeps its proper 4xx status instead of falling into the 500 catch-all.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        String message = status != null && status.is4xxClientError() ? status.getReasonPhrase() : "An unexpected error occurred";
        if (statusCode.is5xxServerError()) {
            log.error("Server error ({})", ex.getClass().getSimpleName(), ex);
        }
        return ResponseEntity.status(statusCode).headers(headers)
                .body(ApiResponse.error(ErrorCodes.forHttpStatus(statusCode.value()), message));
    }

    /**
     * Every expected failure of every module (including {@code AuthException} and the slot / circle exceptions).
     * Body: {@code code}, English fallback {@code message}, optional {@code data} (field-code map, retryAfterSeconds);
     * {@code Retry-After} header when the exception carries a delay.
     */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Object>> handleApiException(ApiException ex) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(ex.getStatus());
        if (ex.getRetryAfterSeconds() != null) {
            builder.header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()));
        }
        return builder.body(ApiResponse.error(ex.getCode(), ex.getMessage(), ex.getData()));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthenticationException(AuthenticationException ex) {
        // Log real reason server-side; never expose internals to the client
        log.warn("Authentication failure: {}", ex.getClass().getSimpleName());
        return error(HttpStatus.UNAUTHORIZED, ErrorCodes.AUTH_INVALID_CREDENTIALS, "Invalid credentials");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(AccessDeniedException ex) {
        log.warn("Access denied ({})", ex.getClass().getSimpleName());
        return error(HttpStatus.FORBIDDEN, ErrorCodes.AUTH_FORBIDDEN, "Access denied");
    }

    /**
     * A unique/foreign-key violation that slipped past the service-level checks (typically a race between two
     * identical requests) is a client conflict, not a server fault. The driver message can contain personal data
     * ("Key (phone_number)=(07...) already exists"), so only the exception type is logged.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException ex) {
        Throwable root = ex.getMostSpecificCause();
        log.warn("Data integrity violation ({})", root.getClass().getSimpleName());
        return error(HttpStatus.CONFLICT, ErrorCodes.DATA_CONFLICT, "The request conflicts with existing data");
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleOptimisticLock(OptimisticLockingFailureException ex) {
        log.warn("Optimistic locking failure ({})", ex.getClass().getSimpleName());
        return error(HttpStatus.CONFLICT, ErrorCodes.DATA_CONFLICT, "The data was changed by someone else, please retry");
    }

    /**
     * Catch-all for unhandled RuntimeExceptions.
     * The actual exception message is logged but NEVER returned to the client
     * to prevent internal state, SQL errors, or stack details from leaking.
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Void>> handleRuntimeException(RuntimeException ex) {
        log.error("Unhandled runtime exception ({})", ex.getClass().getSimpleName(), ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCodes.INTERNAL_ERROR, "An unexpected error occurred");
    }

    /**
     * Final backstop for all other checked exceptions.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception ex) {
        log.error("Unhandled exception ({})", ex.getClass().getSimpleName(), ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCodes.INTERNAL_ERROR, "An unexpected error occurred");
    }

    private static ResponseEntity<ApiResponse<Void>> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(ApiResponse.error(code, message));
    }
}
