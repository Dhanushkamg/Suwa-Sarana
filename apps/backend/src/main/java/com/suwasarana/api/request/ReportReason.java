package com.suwasarana.api.request;

/** Reason a user gives when reporting a request as abusive or fake. */
public enum ReportReason {
    FAKE_OR_MISLEADING,
    DUPLICATE,
    SPAM,
    ABUSIVE_CONTENT,
    OTHER
}
