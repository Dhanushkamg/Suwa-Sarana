package com.suwasarana.api.shared;

import com.suwasarana.api.common.ApiException;
import org.springframework.http.HttpStatus;

/**
 * An {@link ApiException} that also carries a machine-readable {@link ErrorCodes} value.
 * {@link CodedExceptionAdvice} renders the code in the response body ("code" field), so the frontend can localise
 * the message. When the platform error envelope grows a native {@code code}, this class can delegate to it.
 */
public class CodedApiException extends ApiException {

    private final String code;

    public CodedApiException(HttpStatus status, String code, String message) {
        super(status, message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static CodedApiException badRequest(String code, String message) {
        return new CodedApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    public static CodedApiException forbidden(String code, String message) {
        return new CodedApiException(HttpStatus.FORBIDDEN, code, message);
    }

    public static CodedApiException notFound(String code, String message) {
        return new CodedApiException(HttpStatus.NOT_FOUND, code, message);
    }

    public static CodedApiException conflict(String code, String message) {
        return new CodedApiException(HttpStatus.CONFLICT, code, message);
    }

    public static CodedApiException tooManyRequests(String code, String message) {
        return new CodedApiException(HttpStatus.TOO_MANY_REQUESTS, code, message);
    }

    public static CodedApiException unprocessable(String code, String message) {
        return new CodedApiException(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
    }
}
