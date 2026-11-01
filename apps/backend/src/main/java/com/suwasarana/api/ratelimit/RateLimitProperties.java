package com.suwasarana.api.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration of the abuse-protection layer ({@code app.rate-limit.*}). Every value has a safe default,
 * so no configuration is required.
 *
 * <pre>
 * app.rate-limit.enabled=true
 * app.rate-limit.multiplier=1.0                # scale every limit (e.g. 100 for load tests, env APP_RATE_LIMIT_MULTIPLIER)
 * app.rate-limit.trusted-proxies=10.0.0.0/8    # env APP_RATE_LIMIT_TRUSTED_PROXIES, comma separated CIDRs / IPs
 * app.rate-limit.max-buckets=100000
 * app.rate-limit.groups.login.ip-limit=20      # per-group override (keys: see RateGroup)
 * app.rate-limit.groups.login.ip-window=1m
 * app.rate-limit.groups.send-otp.id-limit=3
 * app.rate-limit.groups.send-otp.id-window=1h
 * </pre>
 *
 * A limit of {@code 0} disables that particular check. Scopes:
 * <ul>
 *   <li><b>ip</b> - per client IP (see {@link ClientIpResolver});</li>
 *   <li><b>ipDaily</b> - per client IP per 24 h;</li>
 *   <li><b>id</b> - per identifier in the request (hashed e-mail / phone / circle token);</li>
 *   <li><b>user</b> - per authenticated user (checked after authentication);</li>
 *   <li><b>userDaily</b> - per authenticated user per 24 h.</li>
 * </ul>
 */
@Component
@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {

    private static final Duration ONE_MINUTE = Duration.ofMinutes(1);
    private static final Duration TEN_MINUTES = Duration.ofMinutes(10);
    private static final Duration ONE_HOUR = Duration.ofHours(1);
    private static final Duration FIFTEEN_MINUTES = Duration.ofMinutes(15);

    private boolean enabled = true;
    private double multiplier = 1.0;
    /** CIDR blocks / addresses of reverse proxies whose X-Forwarded-For header is believed. Empty = never. */
    private List<String> trustedProxies = new ArrayList<>();
    /** Hard cap on the number of live buckets; the least recently used ones are evicted beyond it. */
    private int maxBuckets = 100_000;
    /** Largest JSON body inspected for an identifier (larger bodies are only limited per IP). */
    private int maxBodyBytes = 16 * 1024;
    private Map<String, Rule> groups = new HashMap<>();

    /** Per-group overrides; {@code null} fields fall back to the built-in default of that group. */
    public static class Rule {
        private Integer ipLimit;
        private Duration ipWindow;
        private Integer ipDailyLimit;
        private Integer idLimit;
        private Duration idWindow;
        private Integer userLimit;
        private Duration userWindow;
        private Integer userDailyLimit;

        public Integer getIpLimit() { return ipLimit; }
        public void setIpLimit(Integer ipLimit) { this.ipLimit = ipLimit; }
        public Duration getIpWindow() { return ipWindow; }
        public void setIpWindow(Duration ipWindow) { this.ipWindow = ipWindow; }
        public Integer getIpDailyLimit() { return ipDailyLimit; }
        public void setIpDailyLimit(Integer ipDailyLimit) { this.ipDailyLimit = ipDailyLimit; }
        public Integer getIdLimit() { return idLimit; }
        public void setIdLimit(Integer idLimit) { this.idLimit = idLimit; }
        public Duration getIdWindow() { return idWindow; }
        public void setIdWindow(Duration idWindow) { this.idWindow = idWindow; }
        public Integer getUserLimit() { return userLimit; }
        public void setUserLimit(Integer userLimit) { this.userLimit = userLimit; }
        public Duration getUserWindow() { return userWindow; }
        public void setUserWindow(Duration userWindow) { this.userWindow = userWindow; }
        public Integer getUserDailyLimit() { return userDailyLimit; }
        public void setUserDailyLimit(Integer userDailyLimit) { this.userDailyLimit = userDailyLimit; }
    }

    /** The resolved limits of one group (all overrides and the multiplier applied). */
    public record Effective(int ipLimit, Duration ipWindow, int ipDailyLimit,
                            int idLimit, Duration idWindow,
                            int userLimit, Duration userWindow, int userDailyLimit) {
        public boolean hasIp() { return ipLimit > 0; }
        public boolean hasIpDaily() { return ipDailyLimit > 0; }
        public boolean hasId() { return idLimit > 0; }
        public boolean hasUser() { return userLimit > 0; }
        public boolean hasUserDaily() { return userDailyLimit > 0; }
        public boolean hasUserScope() { return hasUser() || hasUserDaily(); }
    }

    private static final Map<RateGroup, Effective> DEFAULTS = new EnumMap<>(RateGroup.class);

    static {
        //                                    ip limit/window     ipDaily   id limit/window         user limit/window      userDaily
        DEFAULTS.put(RateGroup.LOGIN,          new Effective(20, ONE_MINUTE,    0,   10, FIFTEEN_MINUTES, 0, ONE_MINUTE,  0));
        DEFAULTS.put(RateGroup.REGISTER,       new Effective(10, TEN_MINUTES,   0,    5, ONE_HOUR,        0, ONE_MINUTE,  0));
        DEFAULTS.put(RateGroup.SEND_OTP,       new Effective(10, TEN_MINUTES,   0,    3, ONE_HOUR,        0, ONE_MINUTE,  0));
        DEFAULTS.put(RateGroup.VERIFY_OTP,     new Effective(30, TEN_MINUTES,   0,   10, TEN_MINUTES,     0, ONE_MINUTE,  0));
        DEFAULTS.put(RateGroup.RESET_PASSWORD, new Effective(10, TEN_MINUTES,   0,    5, ONE_HOUR,        0, ONE_MINUTE,  0));
        DEFAULTS.put(RateGroup.GOOGLE,         new Effective(20, ONE_MINUTE,    0,    0, ONE_HOUR,        0, ONE_MINUTE,  0));
        DEFAULTS.put(RateGroup.AUTH_OTHER,     new Effective(10, TEN_MINUTES,   0,    5, ONE_HOUR,        0, ONE_MINUTE,  0));
        DEFAULTS.put(RateGroup.FAQ,            new Effective(10, ONE_MINUTE,  300,    0, ONE_HOUR,        0, ONE_MINUTE,  0));
        DEFAULTS.put(RateGroup.AI_DRAFT,       new Effective(20, ONE_MINUTE,  200,    0, ONE_HOUR,        5, ONE_MINUTE, 50));
        DEFAULTS.put(RateGroup.POSTER_COPY,    new Effective(20, ONE_MINUTE,  200,    0, ONE_HOUR,        5, ONE_MINUTE, 50));
        DEFAULTS.put(RateGroup.CONTACT,        new Effective( 5, ONE_HOUR,     20,    0, ONE_HOUR,        0, ONE_MINUTE,  0));
        DEFAULTS.put(RateGroup.CIRCLE_RESPOND, new Effective(10, TEN_MINUTES,  60,   30, ONE_HOUR,        0, ONE_MINUTE,  0));
        DEFAULTS.put(RateGroup.REQUEST_CREATE, new Effective(20, TEN_MINUTES,   0,    0, ONE_HOUR,        5, TEN_MINUTES, 20));
        DEFAULTS.put(RateGroup.REQUEST_REPORT, new Effective(20, TEN_MINUTES,   0,    0, ONE_HOUR,        5, TEN_MINUTES, 20));
        DEFAULTS.put(RateGroup.IVR,            new Effective(120, ONE_MINUTE,   0,    0, ONE_HOUR,        0, ONE_MINUTE,  0));
    }

    /** Resolves the effective limits of a group: built-in default, overridden by configuration, scaled by the multiplier. */
    public Effective effective(RateGroup group) {
        Effective d = DEFAULTS.get(group);
        Rule o = findOverride(group);
        if (o == null && multiplier == 1.0) {
            return d;
        }
        return new Effective(
                scale(pick(o == null ? null : o.ipLimit, d.ipLimit())),
                pick(o == null ? null : o.ipWindow, d.ipWindow()),
                scale(pick(o == null ? null : o.ipDailyLimit, d.ipDailyLimit())),
                scale(pick(o == null ? null : o.idLimit, d.idLimit())),
                pick(o == null ? null : o.idWindow, d.idWindow()),
                scale(pick(o == null ? null : o.userLimit, d.userLimit())),
                pick(o == null ? null : o.userWindow, d.userWindow()),
                scale(pick(o == null ? null : o.userDailyLimit, d.userDailyLimit())));
    }

    private Rule findOverride(RateGroup group) {
        String wanted = RateGroup.normaliseKey(group.key());
        for (Map.Entry<String, Rule> e : groups.entrySet()) {
            if (RateGroup.normaliseKey(e.getKey()).equals(wanted)) {
                return e.getValue();
            }
        }
        return null;
    }

    private int scale(int limit) {
        if (limit <= 0) {
            return 0;
        }
        return (int) Math.max(1, Math.round(limit * multiplier));
    }

    private static <T> T pick(T override, T fallback) {
        return override != null ? override : fallback;
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public double getMultiplier() { return multiplier; }
    public void setMultiplier(double multiplier) { this.multiplier = multiplier <= 0 ? 1.0 : multiplier; }
    public List<String> getTrustedProxies() { return trustedProxies; }
    public void setTrustedProxies(List<String> trustedProxies) { this.trustedProxies = trustedProxies == null ? new ArrayList<>() : trustedProxies; }
    public int getMaxBuckets() { return maxBuckets; }
    public void setMaxBuckets(int maxBuckets) { this.maxBuckets = Math.max(100, maxBuckets); }
    public int getMaxBodyBytes() { return maxBodyBytes; }
    public void setMaxBodyBytes(int maxBodyBytes) { this.maxBodyBytes = Math.max(0, maxBodyBytes); }
    public Map<String, Rule> getGroups() { return groups; }
    public void setGroups(Map<String, Rule> groups) { this.groups = groups == null ? new HashMap<>() : groups; }
}
