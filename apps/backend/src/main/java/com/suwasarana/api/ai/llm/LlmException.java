package com.suwasarana.api.ai.llm;

/** A failed or refused LLM call. The message never contains the prompt or the provider's response body. */
public class LlmException extends Exception {

    /** Coarse failure category (for metrics and logs). */
    public enum Reason { DISABLED, BREAKER_OPEN, BUDGET, TIMEOUT, HTTP, PARSE, INTERRUPTED }

    private final Reason reason;

    public LlmException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public LlmException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
