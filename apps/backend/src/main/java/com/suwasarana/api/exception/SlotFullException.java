package com.suwasarana.api.exception;

public class SlotFullException extends RuntimeException {
    public SlotFullException() {
        super("This donation slot is already fully booked.");
    }
}
