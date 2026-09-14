package com.suwasarana.api.exception;

public class DuplicateBookingException extends RuntimeException {
    public DuplicateBookingException() {
        super("You have already booked this donation slot.");
    }
}
