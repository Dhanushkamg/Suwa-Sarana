package com.suwasarana.api.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Settings of the auth module (prefix {@code app.auth}); defaults and env-var names are in suwa-auth-defaults.properties. */
@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {

    /** Built-in development secret; production must override it (checked in {@link AuthConfig}). */
    public static final String DEV_HMAC_SECRET = "dev-only-hmac-secret-do-not-use-in-production-0123456789";

    private String hmacSecret = DEV_HMAC_SECRET;
    private String frontendUrl = "http://localhost:3000";
    private String consentVersion = "2026-01";
    private String adminBootstrapEmail = "";
    private int emailVerificationTtlHours = 24;
    private int passwordResetTtlMinutes = 30;
    private int adminInviteTtlHours = 72;
    private int resendCooldownSeconds = 60;
    private int refreshReuseGraceSeconds = 5;
    private final Lockout lockout = new Lockout();

    public static class Lockout {
        /** Consecutive failures before the first lock. */
        private int threshold = 5;
        /** First lock duration; doubles for every further {@code threshold} failures. */
        private int baseSeconds = 60;
        private int maxSeconds = 3600;

        public int getThreshold() { return threshold; }
        public void setThreshold(int threshold) { this.threshold = threshold; }
        public int getBaseSeconds() { return baseSeconds; }
        public void setBaseSeconds(int baseSeconds) { this.baseSeconds = baseSeconds; }
        public int getMaxSeconds() { return maxSeconds; }
        public void setMaxSeconds(int maxSeconds) { this.maxSeconds = maxSeconds; }
    }

    public String getHmacSecret() { return hmacSecret; }
    public void setHmacSecret(String hmacSecret) { this.hmacSecret = hmacSecret; }
    public String getFrontendUrl() { return frontendUrl; }
    public void setFrontendUrl(String frontendUrl) { this.frontendUrl = frontendUrl; }
    public String getConsentVersion() { return consentVersion; }
    public void setConsentVersion(String consentVersion) { this.consentVersion = consentVersion; }
    public String getAdminBootstrapEmail() { return adminBootstrapEmail; }
    public void setAdminBootstrapEmail(String adminBootstrapEmail) { this.adminBootstrapEmail = adminBootstrapEmail; }
    public int getEmailVerificationTtlHours() { return emailVerificationTtlHours; }
    public void setEmailVerificationTtlHours(int v) { this.emailVerificationTtlHours = v; }
    public int getPasswordResetTtlMinutes() { return passwordResetTtlMinutes; }
    public void setPasswordResetTtlMinutes(int v) { this.passwordResetTtlMinutes = v; }
    public int getAdminInviteTtlHours() { return adminInviteTtlHours; }
    public void setAdminInviteTtlHours(int v) { this.adminInviteTtlHours = v; }
    public int getResendCooldownSeconds() { return resendCooldownSeconds; }
    public void setResendCooldownSeconds(int v) { this.resendCooldownSeconds = v; }
    public int getRefreshReuseGraceSeconds() { return refreshReuseGraceSeconds; }
    public void setRefreshReuseGraceSeconds(int v) { this.refreshReuseGraceSeconds = v; }
    public Lockout getLockout() { return lockout; }

    /** Frontend base URL without a trailing slash. */
    public String frontendBase() {
        String url = frontendUrl == null ? "" : frontendUrl.trim();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
