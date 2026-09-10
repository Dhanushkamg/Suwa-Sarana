package com.suwasarana.api.exception;

public class RequesterNotVerifiedException extends RuntimeException {
    public RequesterNotVerifiedException(String message) {
        super(message);
    }
}
