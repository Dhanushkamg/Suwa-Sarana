package com.suwasarana.api.exception;

import com.suwasarana.api.common.ApiException;
import com.suwasarana.api.common.ErrorCodes;
import org.springframework.http.HttpStatus;

public class SlotNotFoundException extends ApiException {
    public SlotNotFoundException(Long id) {
        super(HttpStatus.NOT_FOUND, ErrorCodes.SLOT_NOT_FOUND, "Donation slot not found: " + id);
    }
}
