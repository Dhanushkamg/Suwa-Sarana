package com.suwasarana.api.exception;

import java.util.List;

public class DonorIneligibleException extends RuntimeException {

    private final List<String> reasons;

    public DonorIneligibleException(List<String> reasons) {
        super("Donor is currently ineligible to donate: " + String.join(", ", reasons));
        this.reasons = reasons;
    }

    public List<String> getReasons() {
        return reasons;
    }
}
