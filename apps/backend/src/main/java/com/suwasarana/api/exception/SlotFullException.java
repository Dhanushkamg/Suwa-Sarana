package com.suwasarana.api.exception;

import com.suwasarana.api.common.ApiException;
import com.suwasarana.api.common.ErrorCodes;
import org.springframework.http.HttpStatus;

public class SlotFullException extends ApiException {
    public SlotFullException() {
        super(HttpStatus.CONFLICT, ErrorCodes.SLOT_FULL, "This donation slot is already fully booked.");
    }
}
