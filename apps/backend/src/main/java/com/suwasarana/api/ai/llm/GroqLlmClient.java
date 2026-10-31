package com.suwasarana.api.ai.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.suwasarana.api.ai.AiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.regex.Pattern;

/**
 * Groq (OpenAI-compatible chat completions) client with the cost and reliability controls the platform needs:
 * request timeouts, one retry with backoff on transient errors, a consecutive-failure circuit breaker, a daily token
 * budget and {@code max_tokens}. It never logs prompts or response bodies (they can contain health information).
 */
@Component
public class GroqLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(GroqLlmClient.class);
    private static final Pattern THINK = Pattern.compile("(?s)<think>.*?</think>");
    private static final ZoneId COLOMBO = ZoneId.of("Asia/Colombo");

    /** HTTP seam for tests. */
    @FunctionalInterface
    public interface Transport {
        Response post(String url, String bearerKey, String jsonBody, int connectMs, int readMs) throws IOException, InterruptedException;
    }

    /** A raw HTTP response. */
    public record Response(int status, String body) {
    }

    private final AiProperties.Groq cfg;
    private final String apiKey;
    private final Transport transport;
    private final ObjectMapper mapper = new ObjectMapper();
    private final CircuitBreaker breaker;
    private final TokenBudget budget;

    public GroqLlmClient(AiProperties props, Environment env) {
        this(props.getGroq(), props.getGroq().resolveApiKey(env), defaultTransport(), Clock.systemUTC());
    }

    GroqLlmClient(AiProperties.Groq cfg, String apiKey, Transport transport, Clock clock) {
        this.cfg = cfg;
        this.apiKey = apiKey == null ? "" : apiKey;
        this.transport = transport;
        this.breaker = new CircuitBreaker(cfg.getBreakerFailureThreshold(), Duration.ofSeconds(cfg.getBreakerOpenSeconds()), clock);
        this.budget = new TokenBudget(cfg.getDailyTokenBudget(), clock, COLOMBO);
        if (cfg.isEnabled() && this.apiKey.isBlank()) {
            log.warn("GROQ_API_KEY is not set: AI features answer deterministically (no LLM polish / extraction).");
        }
    }

    private static Transport defaultTransport() {
        return (url, key, body, connectMs, readMs) -> {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(connectMs)).build();
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMillis(readMs))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + key)
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new Response(res.statusCode(), res.body());
        };
    }

    @Override
    public boolean isAvailable() {
        return cfg.isEnabled() && !apiKey.isBlank();
    }

    public CircuitBreaker breaker() { return breaker; }
    public TokenBudget budget() { return budget; }

    @Override
    public LlmResult complete(String system, String user, int maxTokens, boolean jsonObject, String model) throws LlmException {
        if (!isAvailable()) throw new LlmException(LlmException.Reason.DISABLED, "LLM disabled or no API key");
        if (!breaker.allow()) throw new LlmException(LlmException.Reason.BREAKER_OPEN, "LLM circuit breaker is open");
        int cap = maxTokens > 0 ? Math.min(maxTokens, cfg.getMaxTokens()) : cfg.getMaxTokens();
        long estimate = (system.length() + user.length()) / 3L + cap;
        if (!budget.tryReserve(estimate)) {
            breaker.onSuccess(); // a budget refusal is not a provider failure; releases a half-open probe
            throw new LlmException(LlmException.Reason.BUDGET, "Daily LLM token budget exhausted");
        }
        String body = buildBody(system, user, cap, jsonObject, model == null || model.isBlank() ? cfg.getModel() : model);
        long started = System.nanoTime();
        int attempts = Math.max(0, cfg.getRetries()) + 1;
        LlmException last = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                Response res = transport.post(cfg.getUrl(), apiKey, body, cfg.getConnectTimeoutMs(), cfg.getReadTimeoutMs());
                if (res.status() == 429 || res.status() >= 500) {
                    last = new LlmException(LlmException.Reason.HTTP, "Provider status " + res.status());
                } else if (res.status() < 200 || res.status() >= 300) {
                    // client error: retrying will not help
                    throw new LlmException(LlmException.Reason.HTTP, "Provider status " + res.status());
                } else {
                    LlmResult parsed = parse(res.body(), (System.nanoTime() - started) / 1_000_000L);
                    budget.settle(estimate, parsed.totalTokens() > 0 ? parsed.totalTokens() : estimate);
                    breaker.onSuccess();
                    log.info("LLM call ok model={} tokens={} ms={}", model == null || model.isBlank() ? cfg.getModel() : model, parsed.totalTokens(), parsed.latencyMs());
                    return parsed;
                }
            } catch (LlmException e) {
                // non-retryable: a 4xx answer or an unreadable body (tokens were probably spent in the latter case)
                budget.settle(estimate, e.getReason() == LlmException.Reason.PARSE ? estimate : 0);
                breaker.onFailure();
                throw e;
            } catch (HttpTimeoutException e) {
                last = new LlmException(LlmException.Reason.TIMEOUT, "Provider timeout", e);
            } catch (IOException e) {
                last = new LlmException(LlmException.Reason.HTTP, "Provider I/O error: " + e.getClass().getSimpleName(), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                budget.settle(estimate, 0);
                breaker.onSuccess();
                throw new LlmException(LlmException.Reason.INTERRUPTED, "Interrupted", e);
            }
            if (attempt < attempts) {
                try {
                    Thread.sleep(cfg.getRetryBackoffMs() * attempt);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        budget.settle(estimate, 0);
        breaker.onFailure();
        log.warn("LLM call failed reason={} attempts={}", last == null ? "?" : last.getReason(), attempts);
        throw last != null ? last : new LlmException(LlmException.Reason.HTTP, "Provider call failed");
    }

    private String buildBody(String system, String user, int maxTokens, boolean jsonObject, String model) throws LlmException {
        ObjectNode root = mapper.createObjectNode();
        root.put("model", model);
        root.put("temperature", cfg.getTemperature());
        root.put("max_tokens", maxTokens);
        if (cfg.getReasoningEffort() != null && !cfg.getReasoningEffort().isBlank()) {
            root.put("reasoning_effort", cfg.getReasoningEffort());
        }
        if (jsonObject) root.putObject("response_format").put("type", "json_object");
        ArrayNode msgs = root.putArray("messages");
        msgs.addObject().put("role", "system").put("content", system);
        msgs.addObject().put("role", "user").put("content", user);
        try {
            return mapper.writeValueAsString(root);
        } catch (IOException e) {
            throw new LlmException(LlmException.Reason.PARSE, "Cannot build request", e);
        }
    }

    private LlmResult parse(String body, long ms) throws LlmException {
        try {
            JsonNode root = mapper.readTree(body);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                throw new LlmException(LlmException.Reason.PARSE, "Provider returned no choices");
            }
            String text = choices.get(0).path("message").path("content").asText("");
            text = THINK.matcher(text).replaceAll("").trim();
            if (text.isEmpty()) throw new LlmException(LlmException.Reason.PARSE, "Provider returned empty content");
            return new LlmResult(text, root.path("usage").path("total_tokens").asLong(0), ms);
        } catch (IOException e) {
            throw new LlmException(LlmException.Reason.PARSE, "Provider returned invalid JSON", e);
        }
    }
}
