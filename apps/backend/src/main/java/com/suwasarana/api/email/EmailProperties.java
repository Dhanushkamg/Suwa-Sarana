package com.suwasarana.api.email;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Email settings (prefix {@code app.email}). {@code provider}: {@code smtp} or {@code logging}. */
@ConfigurationProperties(prefix = "app.email")
public class EmailProperties {

    private String provider = "logging";
    private String from = "Suwa Sarana <no-reply@suwasarana.lk>";
    private final Smtp smtp = new Smtp();

    public static class Smtp {
        private String host = "localhost";
        private int port = 587;
        private String username = "";
        private String password = "";
        private boolean starttls = true;
        private boolean ssl = false;
        private int connectTimeoutMs = 5000;
        private int readTimeoutMs = 10000;

        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public boolean isStarttls() { return starttls; }
        public void setStarttls(boolean starttls) { this.starttls = starttls; }
        public boolean isSsl() { return ssl; }
        public void setSsl(boolean ssl) { this.ssl = ssl; }
        public int getConnectTimeoutMs() { return connectTimeoutMs; }
        public void setConnectTimeoutMs(int v) { this.connectTimeoutMs = v; }
        public int getReadTimeoutMs() { return readTimeoutMs; }
        public void setReadTimeoutMs(int v) { this.readTimeoutMs = v; }
    }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getFrom() { return from; }
    public void setFrom(String from) { this.from = from; }
    public Smtp getSmtp() { return smtp; }
}
