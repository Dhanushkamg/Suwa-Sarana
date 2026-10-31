package com.suwasarana.api.exception;

import com.suwasarana.api.common.ApiException;
import com.suwasarana.api.common.ErrorCodes;
import org.springframework.http.HttpStatus;

public class CircleExpiredException extends ApiException {
    public CircleExpiredException(String message) {
        super(HttpStatus.GONE, ErrorCodes.CIRCLE_EXPIRED, message);
    }
}
