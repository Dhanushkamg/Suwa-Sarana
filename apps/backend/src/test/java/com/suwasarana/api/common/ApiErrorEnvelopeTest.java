package com.suwasarana.api.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** The error envelope: machine-readable codes pass through, and dates are always ISO-8601 strings. */
@SpringBootTest
@ActiveProfiles("test")
class ApiErrorEnvelopeTest {

    @Autowired
    private JsonMapper json;

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("ApiException(status, code, message) passes status, code and message through")
    void apiExceptionWithCode() {
        ResponseEntity<ApiResponse<Object>> response =
                handler.handleApiException(new ApiException(HttpStatus.GONE, "auth.otp_expired", "Code expired"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GONE);
        assertThat(response.getBody().getCode()).isEqualTo("auth.otp_expired");
        assertThat(response.getBody().getMessage()).isEqualTo("Code expired");
        assertThat(response.getBody().isSuccess()).isFalse();
    }

    @Test
    @DisplayName("AuthException is an ApiException: code + English fallback message + field-code data + Retry-After")
    void authExceptionUsesTheSingleMechanism() {
        ResponseEntity<ApiResponse<Object>> locked = handler.handleApiException(
                com.suwasarana.api.auth.AuthException.tooManyRequests(com.suwasarana.api.auth.AuthCodes.ACCOUNT_LOCKED, 90));
        assertThat(locked.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(locked.getHeaders().getFirst("Retry-After")).isEqualTo("90");
        assertThat(locked.getBody().getCode()).isEqualTo("auth.account.locked");
        assertThat(locked.getBody().getMessage()).isEqualTo("Account temporarily locked after too many failed attempts");
        assertThat(locked.getBody().getData()).isEqualTo(java.util.Map.of("retryAfterSeconds", 90));

        ResponseEntity<ApiResponse<Object>> field = handler.handleApiException(
                com.suwasarana.api.auth.AuthException.invalidField("password", com.suwasarana.api.auth.AuthCodes.PASSWORD_COMMON));
        assertThat(field.getBody().getCode()).isEqualTo("validation.failed");
        assertThat(field.getBody().getMessage()).isEqualTo("Validation failed");
        assertThat(field.getBody().getData()).isEqualTo(java.util.Map.of("password", "auth.password.common"));
    }

    @Test
    @DisplayName("every code in ErrorCodes and AuthCodes has an English fallback text")
    void everyCodeHasAnEnglishFallback() throws Exception {
        for (Class<?> holder : new Class<?>[] {ErrorCodes.class, com.suwasarana.api.auth.AuthCodes.class}) {
            for (java.lang.reflect.Field f : holder.getDeclaredFields()) {
                if (f.getType() == String.class && java.lang.reflect.Modifier.isStatic(f.getModifiers()) && java.lang.reflect.Modifier.isPublic(f.getModifiers())) {
                    String code = (String) f.get(null);
                    assertThat(ErrorMessages.english(code)).as("English fallback for " + holder.getSimpleName() + "." + f.getName() + " = " + code)
                            .isNotEqualTo(code);
                }
            }
        }
    }

    @Test
    @DisplayName("factory overloads carry the code; the old factories still work without one")
    void factories() {
        assertThat(ApiException.conflict("slot.full", "Full").getCode()).isEqualTo("slot.full");
        assertThat(ApiException.conflict("Full").getCode()).isNull();
        assertThat(ApiException.badRequest("Bad").getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ApiException.forbidden("x.y", "No").getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ApiException.notFound("x.y", "No").getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ApiException.tooManyRequests("rate_limit.exceeded", "Slow down").getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(ApiException.unauthorized("a.b", "No").getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("a code-less error serialises without a code field; with a code it is present")
    void codeIsOptionalInJson() throws Exception {
        JsonNode withoutCode = json.readTree(json.writeValueAsString(ApiResponse.error("Nope")));
        assertThat(withoutCode.has("code")).isFalse();
        assertThat(withoutCode.get("success").asBoolean()).isFalse();

        JsonNode withCode = json.readTree(json.writeValueAsString(ApiResponse.error("x.y", "Nope")));
        assertThat(withCode.get("code").asString()).isEqualTo("x.y");
    }

    @Test
    @DisplayName("success responses are unchanged: no code, no requestId")
    void successUnchanged() throws Exception {
        MDC.put(RequestContext.MDC_REQUEST_ID, "req-12345678");
        try {
            JsonNode ok = json.readTree(json.writeValueAsString(ApiResponse.success("payload")));
            assertThat(ok.has("code")).isFalse();
            assertThat(ok.has("requestId")).isFalse();
            assertThat(ok.get("data").asString()).isEqualTo("payload");

            JsonNode err = json.readTree(json.writeValueAsString(ApiResponse.error("x.y", "Nope")));
            assertThat(err.get("requestId").asString()).isEqualTo("req-12345678");
        } finally {
            MDC.remove(RequestContext.MDC_REQUEST_ID);
        }
    }

    @Test
    @DisplayName("data integrity violations become 409 data.conflict without leaking driver text")
    void dataIntegrity() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleDataIntegrity(
                new DataIntegrityViolationException("duplicate key value violates unique constraint: Key (phone_number)=(0771234567)"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getCode()).isEqualTo(ErrorCodes.DATA_CONFLICT);
        assertThat(response.getBody().getMessage()).doesNotContain("0771234567");
    }

    @Test
    @DisplayName("java.time values are written as ISO-8601 strings, never as numeric arrays")
    void datesAreIsoStrings() throws Exception {
        assertThat(json.writeValueAsString(LocalDate.of(2026, 10, 28))).isEqualTo("\"2026-10-28\"");
        assertThat(json.writeValueAsString(LocalTime.of(9, 30))).isEqualTo("\"09:30:00\"");
        assertThat(json.writeValueAsString(LocalDateTime.of(2026, 10, 28, 9, 30, 15))).isEqualTo("\"2026-10-28T09:30:15\"");
        assertThat(json.writeValueAsString(Instant.parse("2026-10-28T04:00:00Z"))).isEqualTo("\"2026-10-28T04:00:00Z\"");
        assertThat(json.writeValueAsString(ZonedDateTime.of(2026, 10, 28, 9, 30, 0, 0, ZoneId.of("Asia/Colombo"))))
                .startsWith("\"2026-10-28T09:30:00").doesNotContain("[");

        JsonNode node = json.readTree(json.writeValueAsString(ApiResponse.success("x")));
        assertThat(node.get("timestamp").isString()).isTrue();
        assertThat(node.get("timestamp").asString()).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z");
    }
}
