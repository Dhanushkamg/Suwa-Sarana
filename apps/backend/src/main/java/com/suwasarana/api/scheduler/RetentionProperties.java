package com.suwasarana.api.scheduler;

import com.suwasarana.api.request.RequestStatus;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.EnumSet;
import java.util.Set;

/**
 * Retention periods ({@code app.retention.*}). The table in {@code docs/RETENTION.md} documents the purpose of
 * every item and is the human-readable counterpart of these defaults. A period of {@code 0} disables that purge.
 */
@Component
@ConfigurationProperties(prefix = "app.retention")
public class RetentionProperties {

    /** Expired refresh tokens are deleted this long after they expired. */
    private Duration refreshTokenGrace = Duration.ofDays(1);

    /** OTP rows (phone numbers and codes) are deleted this long after creation. */
    private Duration otpRetention = Duration.ofDays(1);

    /** Handled contact messages are deleted after this many days. */
    private int contactHandledDays = 180;

    /** Contact messages nobody handled are deleted after this many days. */
    private int contactUnhandledDays = 365;

    /** Recipient (phone) and message body of notification logs are removed after this many days. */
    private int notificationLogScrubDays = 90;

    /** Guest names/phones/notes of circle pledges are removed this many days after the request closed. */
    private int circleResponseScrubDays = 30;

    /** Requests in {@link #requestPurgeStatuses} are deleted this many days after their expiry time. */
    private int requestPurgeDays = 30;

    /**
     * Which request statuses are purged. Default: EXPIRED, CANCELLED. Fulfilled requests are kept (donation history).
     * Add MATCHED here only if the request lifecycle can leave a request stuck in that state.
     */
    private Set<RequestStatus> requestPurgeStatuses = EnumSet.of(RequestStatus.EXPIRED, RequestStatus.CANCELLED);

    /** A request in one of these statuses counts as closed for the circle-pledge scrub. */
    private Set<RequestStatus> closedStatuses = EnumSet.of(RequestStatus.CANCELLED, RequestStatus.FULFILLED, RequestStatus.EXPIRED);

    public Duration getRefreshTokenGrace() { return refreshTokenGrace; }
    public void setRefreshTokenGrace(Duration v) { this.refreshTokenGrace = v; }
    public Duration getOtpRetention() { return otpRetention; }
    public void setOtpRetention(Duration v) { this.otpRetention = v; }
    public int getContactHandledDays() { return contactHandledDays; }
    public void setContactHandledDays(int v) { this.contactHandledDays = v; }
    public int getContactUnhandledDays() { return contactUnhandledDays; }
    public void setContactUnhandledDays(int v) { this.contactUnhandledDays = v; }
    public int getNotificationLogScrubDays() { return notificationLogScrubDays; }
    public void setNotificationLogScrubDays(int v) { this.notificationLogScrubDays = v; }
    public int getCircleResponseScrubDays() { return circleResponseScrubDays; }
    public void setCircleResponseScrubDays(int v) { this.circleResponseScrubDays = v; }
    public int getRequestPurgeDays() { return requestPurgeDays; }
    public void setRequestPurgeDays(int v) { this.requestPurgeDays = v; }
    public Set<RequestStatus> getRequestPurgeStatuses() { return requestPurgeStatuses; }
    public void setRequestPurgeStatuses(Set<RequestStatus> v) {
        this.requestPurgeStatuses = v == null || v.isEmpty() ? EnumSet.noneOf(RequestStatus.class) : EnumSet.copyOf(v);
    }
    public Set<RequestStatus> getClosedStatuses() { return closedStatuses; }
    public void setClosedStatuses(Set<RequestStatus> v) {
        this.closedStatuses = v == null || v.isEmpty() ? EnumSet.noneOf(RequestStatus.class) : EnumSet.copyOf(v);
    }
}
