package com.suwasarana.api.exception;

import com.suwasarana.api.common.ApiException;
import com.suwasarana.api.common.ErrorCodes;
import org.springframework.http.HttpStatus;

import java.util.List;

public class DonorIneligibleException extends ApiException {

    private final List<String> reasons;

    public DonorIneligibleException(List<String> reasons) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, ErrorCodes.DONOR_INELIGIBLE,
                "Donor is currently ineligible to donate: " + String.join(", ", reasons));
        this.reasons = reasons;
    }

    public List<String> getReasons() {
        return reasons;
    }
}
