package com.suwasarana.api.request;

/** Why a requester closes a request that is no longer needed. */
public enum CloseReason {
    NO_LONGER_NEEDED,
    FOUND_DONOR_ELSEWHERE,
    OTHER
}
