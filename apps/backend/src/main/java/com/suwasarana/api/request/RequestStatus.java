package com.suwasarana.api.request;

public enum RequestStatus {
    OPEN,
    ESCALATING,
    MATCHED,
    FULFILLED,
    CANCELLED,
    EXPIRED;

    /** Still needs (or is waiting for) a donor. */
    public boolean isActive() {
        return this == OPEN || this == ESCALATING || this == MATCHED;
    }

    /** Finished for good: counts toward the fulfilment-rate denominator. */
    public boolean isClosed() {
        return this == FULFILLED || this == CANCELLED || this == EXPIRED;
    }
}
