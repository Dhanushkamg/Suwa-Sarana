package com.suwasarana.api.circle;

import com.suwasarana.api.request.RequestStatus;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

/**
 * Settings of the replacement-donor circles ({@code app.circle.*}); all have safe defaults.
 */
@Component
@ConfigurationProperties(prefix = "app.circle")
public class CircleProperties {

    /**
     * Key material for the reversible copy of invite tokens (env {@code CIRCLE_TOKEN_KEY}). When blank a key is
     * derived from {@code jwt.secret}. Changing it makes the stored copies unreadable: the owner then simply gets a
     * freshly generated link on the next request (the old link stops working).
     */
    private String tokenKey = "";

    /** Hard cap on guest pledges per circle. */
    private int maxPledgesPerCircle = 50;

    /** How many times one phone number may pledge to the same circle. */
    private int maxPledgesPerPhonePerCircle = 1;

    /** How many pledges one phone number may make across all circles in 24 hours. */
    private int maxPledgesPerPhonePerDay = 5;

    /** Invites only work while the request is in one of these states. */
    private Set<RequestStatus> activeStatuses = EnumSet.of(RequestStatus.OPEN, RequestStatus.ESCALATING, RequestStatus.MATCHED);

    public String getTokenKey() { return tokenKey; }
    public void setTokenKey(String tokenKey) { this.tokenKey = tokenKey == null ? "" : tokenKey; }
    public int getMaxPledgesPerCircle() { return maxPledgesPerCircle; }
    public void setMaxPledgesPerCircle(int v) { this.maxPledgesPerCircle = Math.max(1, v); }
    public int getMaxPledgesPerPhonePerCircle() { return maxPledgesPerPhonePerCircle; }
    public void setMaxPledgesPerPhonePerCircle(int v) { this.maxPledgesPerPhonePerCircle = Math.max(1, v); }
    public int getMaxPledgesPerPhonePerDay() { return maxPledgesPerPhonePerDay; }
    public void setMaxPledgesPerPhonePerDay(int v) { this.maxPledgesPerPhonePerDay = Math.max(1, v); }
    public Set<RequestStatus> getActiveStatuses() { return activeStatuses; }
    public void setActiveStatuses(Set<RequestStatus> s) {
        this.activeStatuses = (s == null || s.isEmpty()) ? EnumSet.noneOf(RequestStatus.class) : EnumSet.copyOf(s);
    }

    public boolean isActive(RequestStatus status) {
        return status != null && activeStatuses.contains(status);
    }
}
