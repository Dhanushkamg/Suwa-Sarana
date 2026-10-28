package com.suwasarana.api.ivr;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Settings of the phone hotline webhook ({@code app.ivr.*}).
 *
 * <pre>
 * app.ivr.auth-token=...            # Twilio auth token (env TWILIO_AUTH_TOKEN); required unless signature-required=false
 * app.ivr.public-url=https://api.example.lk/api/ivr/webhook   # the URL configured in the Twilio console
 * app.ivr.signature-required=true   # set false ONLY for local development without Twilio
 * app.ivr.coordinator-number=+94112369931
 * </pre>
 */
@Component
@ConfigurationProperties(prefix = "app.ivr")
public class IvrProperties {

    private static final Logger log = LoggerFactory.getLogger(IvrProperties.class);

    /** Reject webhook calls whose X-Twilio-Signature is missing or wrong. */
    private boolean signatureRequired = true;

    /** Twilio account auth token, the HMAC key of the signature. Never logged. */
    private String authToken = "";

    /**
     * Exact URL Twilio calls (scheme, host, path), as configured in the Twilio console. Needed behind a proxy or load
     * balancer, where the URL seen by the application differs from the one Twilio signed. Blank = use the request URL.
     */
    private String publicUrl = "";

    /** Number dialled for "press 3" (the National Blood Centre hotline). */
    private String coordinatorNumber = "+94112369931";

    /** How many verified critical requests are read out at most. */
    private int maxCriticalRequests = 3;

    /** Requests with a fraud-risk score above this are never read out. */
    private int maxFraudRiskScore = 40;

    private String voice = "Polly.Amy";
    private String language = "en-US";

    @PostConstruct
    void warnIfMisconfigured() {
        if (signatureRequired && (authToken == null || authToken.isBlank())) {
            log.warn("app.ivr.signature-required is true but app.ivr.auth-token (TWILIO_AUTH_TOKEN) is not set: "
                    + "every call to /api/ivr/webhook will be rejected until it is configured");
        }
        if (!signatureRequired) {
            log.warn("app.ivr.signature-required=false: the IVR webhook accepts unsigned requests. Never use this in production.");
        }
    }

    public boolean isSignatureRequired() { return signatureRequired; }
    public void setSignatureRequired(boolean signatureRequired) { this.signatureRequired = signatureRequired; }
    public String getAuthToken() { return authToken; }
    public void setAuthToken(String authToken) { this.authToken = authToken == null ? "" : authToken; }
    public String getPublicUrl() { return publicUrl; }
    public void setPublicUrl(String publicUrl) { this.publicUrl = publicUrl == null ? "" : publicUrl.trim(); }
    public String getCoordinatorNumber() { return coordinatorNumber; }
    public void setCoordinatorNumber(String coordinatorNumber) { this.coordinatorNumber = coordinatorNumber == null ? "" : coordinatorNumber.trim(); }
    public int getMaxCriticalRequests() { return maxCriticalRequests; }
    public void setMaxCriticalRequests(int maxCriticalRequests) { this.maxCriticalRequests = Math.max(1, maxCriticalRequests); }
    public int getMaxFraudRiskScore() { return maxFraudRiskScore; }
    public void setMaxFraudRiskScore(int maxFraudRiskScore) { this.maxFraudRiskScore = maxFraudRiskScore; }
    public String getVoice() { return voice; }
    public void setVoice(String voice) { this.voice = voice; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
}
