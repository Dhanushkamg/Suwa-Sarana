package com.suwasarana.api.notification.sms;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** SMS settings (prefix {@code app.sms}). {@code provider}: {@code notifylk} or {@code logging}. */
@ConfigurationProperties(prefix = "app.sms")
public class SmsProperties {

    private String provider = "logging";
    /** Logging provider only: also print the message body (OTP codes!). Always on in dev/local/test profiles. */
    private boolean logMessageBody = false;
    private final NotifyLk notifylk = new NotifyLk();

    public static class NotifyLk {
        private String baseUrl = "https://app.notify.lk/api/v1/send";
        private String userId = "";
        private String apiKey = "";
        private String senderId = "NotifyDEMO";
        private int connectTimeoutMs = 3000;
        private int requestTimeoutMs = 8000;

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getSenderId() { return senderId; }
        public void setSenderId(String senderId) { this.senderId = senderId; }
        public int getConnectTimeoutMs() { return connectTimeoutMs; }
        public void setConnectTimeoutMs(int v) { this.connectTimeoutMs = v; }
        public int getRequestTimeoutMs() { return requestTimeoutMs; }
        public void setRequestTimeoutMs(int v) { this.requestTimeoutMs = v; }
    }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public boolean isLogMessageBody() { return logMessageBody; }
    public void setLogMessageBody(boolean logMessageBody) { this.logMessageBody = logMessageBody; }
    public NotifyLk getNotifylk() { return notifylk; }
}
