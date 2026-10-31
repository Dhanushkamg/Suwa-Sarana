package com.suwasarana.api.exception;

import com.suwasarana.api.common.ApiException;
import com.suwasarana.api.common.ErrorCodes;
import org.springframework.http.HttpStatus;

public class CircleNotFoundException extends ApiException {
    public CircleNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, ErrorCodes.CIRCLE_NOT_FOUND, message);
    }
}
