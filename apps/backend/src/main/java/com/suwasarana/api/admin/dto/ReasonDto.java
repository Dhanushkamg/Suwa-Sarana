package com.suwasarana.api.admin.dto;

import com.suwasarana.api.auth.AuthCodes;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The mandatory justification for an administrative decision; it is stored in the audit log and shown to the user. */
public class ReasonDto {

    @NotBlank(message = AuthCodes.REASON_REQUIRED)
    @Size.List({
            @Size(min = 3, message = AuthCodes.REASON_REQUIRED),
            @Size(max = 500, message = AuthCodes.REASON_TOO_LONG)
    })
    private String reason;

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason == null ? null : reason.trim(); }
}
