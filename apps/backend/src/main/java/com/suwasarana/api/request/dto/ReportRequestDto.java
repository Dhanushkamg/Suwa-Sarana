package com.suwasarana.api.request.dto;

import com.suwasarana.api.request.ReportReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class ReportRequestDto {

    @NotNull(message = "Reason is required")
    private ReportReason reason;

    @Size(max = 500, message = "Details must be at most 500 characters")
    private String details;

    public ReportReason getReason() { return reason; }
    public void setReason(ReportReason reason) { this.reason = reason; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
}
