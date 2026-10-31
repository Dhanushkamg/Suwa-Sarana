package com.suwasarana.api.ai.llm;

/**
 * Minimal abstraction over the LLM provider. Features must work without it: every caller catches
 * {@link LlmException} and falls back to its deterministic path.
 */
public interface LlmClient {

    /** False when the feature is switched off or no API key is configured. */
    boolean isAvailable();

    /**
     * @param model optional model override (blank = the configured default)
     * @throws LlmException on any failure, with a coarse {@link LlmException.Reason}
     */
    LlmResult complete(String system, String user, int maxTokens, boolean jsonObject, String model) throws LlmException;

    /** Result of a completion. */
    record LlmResult(String text, long totalTokens, long latencyMs) {
    }
}
