package com.suwasarana.api.exception;

public class SlotNotFoundException extends RuntimeException {
    public SlotNotFoundException(Long id) {
        super("Donation slot not found: " + id);
    }
}
