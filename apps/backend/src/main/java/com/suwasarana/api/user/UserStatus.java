package com.suwasarana.api.user;

/** Lifecycle of an account, independent of the verification workflow. */
public enum UserStatus {
    ACTIVE,
    /** Blocked by an administrator: cannot sign in, existing tokens stop working. */
    SUSPENDED,
    /** Erased at the owner's request (PII scrubbed, row kept for referential history). */
    DELETED
}
