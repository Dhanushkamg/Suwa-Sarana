package com.suwasarana.api.exception;

import com.suwasarana.api.common.ApiException;
import com.suwasarana.api.common.ErrorCodes;
import org.springframework.http.HttpStatus;

public class RequesterNotVerifiedException extends ApiException {
    public RequesterNotVerifiedException(String message) {
        super(HttpStatus.FORBIDDEN, ErrorCodes.REQUESTER_NOT_VERIFIED, message);
    }
}
