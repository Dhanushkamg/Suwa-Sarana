package com.suwasarana.api.contact;

import jakarta.validation.constraints.NotNull;

public class UpdateContactStatusDto {

    @NotNull(message = "status is required (NEW or HANDLED)")
    private ContactStatus status;

    public ContactStatus getStatus() { return status; }
    public void setStatus(ContactStatus status) { this.status = status; }
}
