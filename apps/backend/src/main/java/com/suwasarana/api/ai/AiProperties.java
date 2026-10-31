package com.suwasarana.api.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Configuration of every AI feature, bound from {@code app.ai.*} (relaxed binding: {@code APP_AI_GROQ_MODEL} ...).
 * Every value has a safe default so the application starts without any {@code application.yml} entry.
 * The Groq key is read from {@code app.ai.groq.api-key}, then {@code groq.api.key}, then the {@code GROQ_API_KEY}
 * environment variable (see {@link Groq#resolveApiKey(Environment)}).
 */
@Component
@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {

    private final Groq groq = new Groq();
    private final Faq faq = new Faq();
    private final Contacts contacts = new Contacts();
    private final Kb kb = new Kb();

    public Groq getGroq() { return groq; }
    public Faq getFaq() { return faq; }
    public Contacts getContacts() { return contacts; }
    public Kb getKb() { return kb; }

    /** Groq (OpenAI-compatible) provider settings and cost controls. */
    public static class Groq {
        /** Master switch. When false (or no key) every feature answers deterministically. */
        private boolean enabled = true;
        private String apiKey = "";
        private String url = "https://api.groq.com/openai/v1/chat/completions";
        /** Production model (not a Preview model). Override with APP_AI_GROQ_MODEL. */
        private String model = "llama-3.3-70b-versatile";
        /** Model for request intake JSON extraction; blank = same as {@link #model}. */
        private String intakeModel = "";
        private int maxTokens = 400;
        private double temperature = 0.2;
        private int connectTimeoutMs = 3000;
        private int readTimeoutMs = 8000;
        /** Retries after the first attempt (transient errors only). */
        private int retries = 1;
        private long retryBackoffMs = 300;
        /** Consecutive failures that open the circuit breaker. */
        private int breakerFailureThreshold = 5;
        private int breakerOpenSeconds = 60;
        /** Total tokens (prompt + completion) allowed per day per application instance. 0 = unlimited. */
        private long dailyTokenBudget = 300_000;
        /** Only sent when non-blank, for reasoning models (for example "none"/"low"). */
        private String reasoningEffort = "";

        /** The key from properties, falling back to {@code groq.api.key} then the {@code GROQ_API_KEY} env var. */
        public String resolveApiKey(Environment env) {
            if (apiKey != null && !apiKey.isBlank()) return apiKey.trim();
            if (env != null) {
                String k = env.getProperty("groq.api.key");
                if (k == null || k.isBlank()) k = env.getProperty("GROQ_API_KEY");
                if (k != null && !k.isBlank()) return k.trim();
            }
            return "";
        }

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public String getIntakeModel() { return intakeModel; }
        public void setIntakeModel(String intakeModel) { this.intakeModel = intakeModel; }
        public String effectiveIntakeModel() { return intakeModel == null || intakeModel.isBlank() ? model : intakeModel; }
        public int getMaxTokens() { return maxTokens; }
        public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
        public double getTemperature() { return temperature; }
        public void setTemperature(double temperature) { this.temperature = temperature; }
        public int getConnectTimeoutMs() { return connectTimeoutMs; }
        public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }
        public int getReadTimeoutMs() { return readTimeoutMs; }
        public void setReadTimeoutMs(int readTimeoutMs) { this.readTimeoutMs = readTimeoutMs; }
        public int getRetries() { return retries; }
        public void setRetries(int retries) { this.retries = retries; }
        public long getRetryBackoffMs() { return retryBackoffMs; }
        public void setRetryBackoffMs(long retryBackoffMs) { this.retryBackoffMs = retryBackoffMs; }
        public int getBreakerFailureThreshold() { return breakerFailureThreshold; }
        public void setBreakerFailureThreshold(int breakerFailureThreshold) { this.breakerFailureThreshold = breakerFailureThreshold; }
        public int getBreakerOpenSeconds() { return breakerOpenSeconds; }
        public void setBreakerOpenSeconds(int breakerOpenSeconds) { this.breakerOpenSeconds = breakerOpenSeconds; }
        public long getDailyTokenBudget() { return dailyTokenBudget; }
        public void setDailyTokenBudget(long dailyTokenBudget) { this.dailyTokenBudget = dailyTokenBudget; }
        public String getReasoningEffort() { return reasoningEffort; }
        public void setReasoningEffort(String reasoningEffort) { this.reasoningEffort = reasoningEffort; }
    }

    /** Eligibility chatbot behaviour. */
    public static class Faq {
        /** How to answer Singlish input when the request does not say: "script" (Sinhala script), "romanised", "english". */
        private String singlishReply = "script";
        /** Let the LLM rephrase VERIFIED answers (never GAP/CONFLICT ones). */
        private boolean llmPolish = true;
        /** Emergency ambulance number shown in the safety reply. OWNER MUST CONFIRM. Blank = generic wording. */
        private String emergencyNumber = "1990";
        private int maxQuestionChars = 500;
        private int maxHistoryTurns = 6;
        private int maxHistoryChars = 500;
        /** Minimum BM25 score for the first intent / for additional intents of one question. */
        private double minScore = 1.4;
        private double minScoreExtra = 1.9;

        public String getSinglishReply() { return singlishReply; }
        public void setSinglishReply(String singlishReply) { this.singlishReply = singlishReply; }
        public boolean isLlmPolish() { return llmPolish; }
        public void setLlmPolish(boolean llmPolish) { this.llmPolish = llmPolish; }
        public String getEmergencyNumber() { return emergencyNumber; }
        public void setEmergencyNumber(String emergencyNumber) { this.emergencyNumber = emergencyNumber; }
        public int getMaxQuestionChars() { return maxQuestionChars; }
        public void setMaxQuestionChars(int maxQuestionChars) { this.maxQuestionChars = maxQuestionChars; }
        public int getMaxHistoryTurns() { return maxHistoryTurns; }
        public void setMaxHistoryTurns(int maxHistoryTurns) { this.maxHistoryTurns = maxHistoryTurns; }
        public int getMaxHistoryChars() { return maxHistoryChars; }
        public void setMaxHistoryChars(int maxHistoryChars) { this.maxHistoryChars = maxHistoryChars; }
        public double getMinScore() { return minScore; }
        public void setMinScore(double minScore) { this.minScore = minScore; }
        public double getMinScoreExtra() { return minScoreExtra; }
        public void setMinScoreExtra(double minScoreExtra) { this.minScoreExtra = minScoreExtra; }
    }

    /** NBTS contact details shown in safe replies (from https://nbts.health.gov.lk/contact-us/, read 2026-10-27). */
    public static class Contacts {
        private String nbtsPhone = "+94 11 236 9931-4";
        private String nbtsEmail = "info@nbts.health.gov.lk";
        private String nbtsAddress = "No. 555/5D, Elvitigala Mawatha, Narahenpita, Colombo 05";
        private String nbtsWebsite = "https://nbts.health.gov.lk/";

        public String getNbtsPhone() { return nbtsPhone; }
        public void setNbtsPhone(String nbtsPhone) { this.nbtsPhone = nbtsPhone; }
        public String getNbtsEmail() { return nbtsEmail; }
        public void setNbtsEmail(String nbtsEmail) { this.nbtsEmail = nbtsEmail; }
        public String getNbtsAddress() { return nbtsAddress; }
        public void setNbtsAddress(String nbtsAddress) { this.nbtsAddress = nbtsAddress; }
        public String getNbtsWebsite() { return nbtsWebsite; }
        public void setNbtsWebsite(String nbtsWebsite) { this.nbtsWebsite = nbtsWebsite; }
    }

    /** Classpath locations of the knowledge-base resources. */
    public static class Kb {
        private String path = "kb/nbts-eligibility.v1.json";

        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
    }
}
