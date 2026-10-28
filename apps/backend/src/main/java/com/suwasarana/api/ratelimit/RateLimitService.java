package com.suwasarana.api.ratelimit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;

/**
 * Applies the configured limits of a {@link RateGroup} to the four scopes (ip, ip-daily, identifier, user).
 * Identifiers (e-mail addresses, phone numbers, circle tokens) are never kept in memory in clear: the bucket key is
 * an HMAC under a random per-process key.
 */
@Service
public class RateLimitService {

    private static final Duration ONE_DAY = Duration.ofDays(1);

    /** Which check refused the request (for logging only). */
    public enum Scope { IP, IP_DAILY, IDENTIFIER, USER, USER_DAILY }

    /** Outcome of a check. {@code allowed} requests carry no other information. */
    public record Decision(boolean allowed, long retryAfterSeconds, Scope scope) {
        static final Decision ALLOWED = new Decision(true, 0, null);
        static Decision denied(long retryAfterSeconds, Scope scope) {
            return new Decision(false, retryAfterSeconds, scope);
        }
    }

    private final RateLimitProperties properties;
    private final BucketStore store;
    private final byte[] hmacKey = new byte[32];

    @Autowired
    public RateLimitService(RateLimitProperties properties) {
        this(properties, new BucketStore(properties.getMaxBuckets()));
    }

    public RateLimitService(RateLimitProperties properties, BucketStore store) {
        this.properties = properties;
        this.store = store;
        new SecureRandom().nextBytes(hmacKey);
    }

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    public Decision checkIp(RateGroup group, String clientIp) {
        RateLimitProperties.Effective rule = properties.effective(group);
        if (rule.hasIp()) {
            BucketStore.Outcome o = store.tryConsume("ip|" + group.key() + "|" + clientIp, rule.ipLimit(), rule.ipWindow());
            if (!o.allowed()) {
                return Decision.denied(o.retryAfterSeconds(), Scope.IP);
            }
        }
        if (rule.hasIpDaily()) {
            BucketStore.Outcome o = store.tryConsume("ipd|" + group.key() + "|" + clientIp, rule.ipDailyLimit(), ONE_DAY);
            if (!o.allowed()) {
                return Decision.denied(o.retryAfterSeconds(), Scope.IP_DAILY);
            }
        }
        return Decision.ALLOWED;
    }

    /** @param identifier an already {@link Identifiers#normalise(String) normalised} identifier */
    public Decision checkIdentifier(RateGroup group, String identifier) {
        RateLimitProperties.Effective rule = properties.effective(group);
        if (!rule.hasId() || identifier == null || identifier.isEmpty()) {
            return Decision.ALLOWED;
        }
        BucketStore.Outcome o = store.tryConsume("id|" + group.key() + "|" + hash(identifier), rule.idLimit(), rule.idWindow());
        return o.allowed() ? Decision.ALLOWED : Decision.denied(o.retryAfterSeconds(), Scope.IDENTIFIER);
    }

    public Decision checkUser(RateGroup group, String userKey) {
        RateLimitProperties.Effective rule = properties.effective(group);
        if (userKey == null || userKey.isEmpty()) {
            return Decision.ALLOWED;
        }
        if (rule.hasUser()) {
            BucketStore.Outcome o = store.tryConsume("u|" + group.key() + "|" + hash(userKey), rule.userLimit(), rule.userWindow());
            if (!o.allowed()) {
                return Decision.denied(o.retryAfterSeconds(), Scope.USER);
            }
        }
        if (rule.hasUserDaily()) {
            BucketStore.Outcome o = store.tryConsume("ud|" + group.key() + "|" + hash(userKey), rule.userDailyLimit(), ONE_DAY);
            if (!o.allowed()) {
                return Decision.denied(o.retryAfterSeconds(), Scope.USER_DAILY);
            }
        }
        return Decision.ALLOWED;
    }

    public RateLimitProperties.Effective rule(RateGroup group) {
        return properties.effective(group);
    }

    public BucketStore store() {
        return store;
    }

    private String hash(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacKey, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)), 0, 16);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }
}
