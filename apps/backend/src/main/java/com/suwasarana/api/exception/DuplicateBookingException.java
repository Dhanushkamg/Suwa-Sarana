package com.suwasarana.api.exception;

import com.suwasarana.api.common.ApiException;
import com.suwasarana.api.common.ErrorCodes;
import org.springframework.http.HttpStatus;

public class DuplicateBookingException extends ApiException {
    public DuplicateBookingException() {
        super(HttpStatus.CONFLICT, ErrorCodes.SLOT_DUPLICATE_BOOKING, "You have already booked this donation slot.");
    }
}
