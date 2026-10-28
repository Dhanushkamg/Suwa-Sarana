package com.suwasarana.api.common;

/** Names shared by the request-id filter, the logging configuration and the error envelope. */
public final class RequestContext {

    private RequestContext() {}

    /** HTTP header carrying the correlation id (accepted from the caller/ALB, always returned). */
    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    /** MDC key under which the id is available to every log line of the request (see logback-spring.xml). */
    public static final String MDC_REQUEST_ID = "requestId";
}
