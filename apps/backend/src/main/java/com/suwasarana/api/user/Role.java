package com.suwasarana.api.user;

public enum Role {
    DONOR,
    REQUESTER,
    HOSPITAL_REQUESTER,
    BLOOD_BANK_REQUESTER,
    ADMIN;

    /** Hospitals and blood banks: roles that publish to many people and therefore need an administrator's approval. */
    public boolean isInstitutional() {
        return this == HOSPITAL_REQUESTER || this == BLOOD_BANK_REQUESTER;
    }
}
