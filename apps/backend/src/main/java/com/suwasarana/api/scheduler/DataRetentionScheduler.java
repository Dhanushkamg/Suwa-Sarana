package com.suwasarana.api.scheduler;

import com.suwasarana.api.request.RequestStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Deletes or scrubs personal data once its retention period is over. What is kept for how long, and why, is listed in
 * {@code docs/RETENTION.md}; the periods are configurable ({@link RetentionProperties}).
 *
 * <p>Every purge is a separate, idempotent SQL statement, so one failing purge does not stop the others and a rerun
 * is harmless. Only counts are logged, never data. A database advisory lock ({@link SchedulerGuard}) prevents several
 * instances from running the same job at once.
 */
@Service
public class DataRetentionScheduler {

    private static final Logger log = LoggerFactory.getLogger(DataRetentionScheduler.class);

    static final String SCRUBBED_NAME = "[removed]";

    private final JdbcTemplate jdbc;
    private final RetentionProperties props;
    private final SchedulerGuard guard;
    private final Clock clock;

    @Autowired
    public DataRetentionScheduler(JdbcTemplate jdbc, RetentionProperties props, SchedulerGuard guard, Clock clock) {
        this.jdbc = jdbc;
        this.props = props;
        this.guard = guard;
        this.clock = clock;
    }

    /** Daily at 02:00 Colombo time (override with app.retention.cron). */
    @Scheduled(cron = "${app.retention.cron:0 0 2 * * *}", zone = "Asia/Colombo")
    public void runDailyRetention() {
        guard.runExclusively("retention-daily", this::runAll);
    }

    /** OTP rows hold phone numbers and codes: purged hourly (override with app.retention.otp-cron). */
    @Scheduled(cron = "${app.retention.otp-cron:0 15 * * * *}", zone = "Asia/Colombo")
    public void purgeOtpHourly() {
        guard.runExclusively("retention-otp", this::purgeOtpVerifications);
    }

    /** Runs every purge once; returns nothing, failures are logged per purge. */
    void runAll() {
        log.info("Data retention run started");
        attempt("refresh tokens", this::purgeExpiredRefreshTokens);
        attempt("otp verifications", this::purgeOtpVerifications);
        attempt("contact messages", this::purgeContactMessages);
        attempt("notification logs", this::scrubNotificationLogs);
        attempt("circle responses", this::scrubCircleResponses);
        attempt("blood requests", this::purgeRequests);
        log.info("Data retention run finished");
    }

    private void attempt(String what, java.util.function.IntSupplier purge) {
        try {
            int n = purge.getAsInt();
            log.info("Retention: {} -> {} row(s) affected", what, n);
        } catch (RuntimeException e) {
            log.error("Retention: {} failed ({})", what, e.getClass().getSimpleName());
        }
    }

    // ------------------------------------------------------------------ individual purges (package-visible for tests)

    /** refresh_tokens whose expiry passed more than the grace period ago. */
    int purgeExpiredRefreshTokens() {
        Timestamp cutoff = before(props.getRefreshTokenGrace());
        return jdbc.update("DELETE FROM refresh_tokens WHERE expires_at < ?", cutoff);
    }

    /** otp_verifications older than the retention period. */
    int purgeOtpVerifications() {
        if (isDisabled(props.getOtpRetention())) {
            return 0;
        }
        Timestamp cutoff = before(props.getOtpRetention());
        return jdbc.update("DELETE FROM otp_verifications WHERE created_at < ?", cutoff);
    }

    /** contact_messages: handled ones after N days, unhandled ones after M days. */
    int purgeContactMessages() {
        int deleted = 0;
        if (props.getContactHandledDays() > 0) {
            deleted += jdbc.update("DELETE FROM contact_messages WHERE status = 'HANDLED' AND handled_at < ?",
                    before(Duration.ofDays(props.getContactHandledDays())));
        }
        if (props.getContactUnhandledDays() > 0) {
            deleted += jdbc.update("DELETE FROM contact_messages WHERE created_at < ?",
                    before(Duration.ofDays(props.getContactUnhandledDays())));
        }
        return deleted;
    }

    /** notification_logs: keep the delivery record (donor, request, channel, status, time) but drop phone and text. */
    int scrubNotificationLogs() {
        if (props.getNotificationLogScrubDays() <= 0) {
            return 0;
        }
        return jdbc.update("UPDATE notification_logs SET recipient = NULL, message_body = NULL "
                        + "WHERE sent_at < ? AND (recipient IS NOT NULL OR message_body IS NOT NULL)",
                before(Duration.ofDays(props.getNotificationLogScrubDays())));
    }

    /**
     * circle_responses: guest name / phone / notes are removed once the circle has been expired, or its request has
     * been closed (cancelled / fulfilled / expired) for the configured number of days. The row stays (blood type and
     * time) so the requester's count does not change.
     */
    int scrubCircleResponses() {
        if (props.getCircleResponseScrubDays() <= 0) {
            return 0;
        }
        Timestamp cutoff = before(Duration.ofDays(props.getCircleResponseScrubDays()));
        Collection<RequestStatus> closed = props.getClosedStatuses();

        List<Object> args = new ArrayList<>();
        args.add(SCRUBBED_NAME);
        args.add(Timestamp.from(clock.instant()));
        StringBuilder sql = new StringBuilder(
                "UPDATE circle_responses SET responder_name = ?, responder_phone = '', notes = NULL, scrubbed_at = ? "
                        + "WHERE scrubbed_at IS NULL AND circle_id IN ("
                        + "SELECT c.id FROM request_circles c JOIN blood_requests r ON r.id = c.request_id "
                        + "WHERE c.expires_at < ?");
        args.add(cutoff);
        if (!closed.isEmpty()) {
            sql.append(" OR (r.expires_at < ? AND r.status IN (").append(placeholders(closed.size())).append("))");
            args.add(cutoff);
            closed.forEach(s -> args.add(s.name()));
        }
        sql.append(")");
        return jdbc.update(sql.toString(), args.toArray());
    }

    /** blood_requests in the purge statuses whose expiry is older than the configured days (children cascade in the DB). */
    int purgeRequests() {
        Set<RequestStatus> statuses = props.getRequestPurgeStatuses();
        if (props.getRequestPurgeDays() <= 0 || statuses.isEmpty()) {
            return 0;
        }
        List<Object> args = new ArrayList<>();
        args.add(before(Duration.ofDays(props.getRequestPurgeDays())));
        statuses.forEach(s -> args.add(s.name()));
        return jdbc.update("DELETE FROM blood_requests WHERE expires_at < ? AND status IN (" + placeholders(statuses.size()) + ")",
                args.toArray());
    }

    // ------------------------------------------------------------------ helpers

    private Timestamp before(Duration age) {
        Instant cutoff = clock.instant().minus(age);
        return Timestamp.from(cutoff);
    }

    private static boolean isDisabled(Duration d) {
        return d == null || d.isZero() || d.isNegative();
    }

    private static String placeholders(int n) {
        return java.util.stream.IntStream.range(0, n).mapToObj(i -> "?").collect(Collectors.joining(", "));
    }
}
