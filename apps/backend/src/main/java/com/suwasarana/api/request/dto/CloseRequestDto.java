package com.suwasarana.api.request.dto;

import com.suwasarana.api.request.CloseReason;

public class CloseRequestDto {

    /** Optional; defaults to NO_LONGER_NEEDED. */
    private CloseReason reason;

    public CloseReason getReason() { return reason; }
    public void setReason(CloseReason reason) { this.reason = reason; }
}
