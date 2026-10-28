package com.suwasarana.api.scheduler;

import com.suwasarana.api.auth.RefreshToken;
import com.suwasarana.api.auth.RefreshTokenRepository;
import com.suwasarana.api.auth.otp.OtpVerification;
import com.suwasarana.api.auth.otp.OtpVerificationRepository;
import com.suwasarana.api.circle.CircleResponse;
import com.suwasarana.api.circle.CircleResponseRepository;
import com.suwasarana.api.circle.RequestCircle;
import com.suwasarana.api.circle.RequestCircleRepository;
import com.suwasarana.api.contact.ContactMessage;
import com.suwasarana.api.contact.ContactRepository;
import com.suwasarana.api.contact.ContactStatus;
import com.suwasarana.api.donor.DonorProfile;
import com.suwasarana.api.donor.DonorRepository;
import com.suwasarana.api.notification.NotificationChannel;
import com.suwasarana.api.notification.NotificationDeliveryStatus;
import com.suwasarana.api.notification.NotificationLog;
import com.suwasarana.api.notification.NotificationLogRepository;
import com.suwasarana.api.request.BloodRequest;
import com.suwasarana.api.request.RequestRepository;
import com.suwasarana.api.request.RequestStatus;
import com.suwasarana.api.user.Role;
import com.suwasarana.api.user.User;
import com.suwasarana.api.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

/** Each retention purge against a real (H2) schema with a fixed clock. */
@SpringBootTest
@ActiveProfiles("test")
class DataRetentionSchedulerTest {

    private static final Instant NOW = Instant.parse("2026-10-28T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneId.of("Asia/Colombo"));

    @Autowired private JdbcTemplate jdbc;
    @Autowired private SchedulerGuard guard;
    @Autowired private UserRepository users;
    @Autowired private DonorRepository donors;
    @Autowired private RequestRepository requests;
    @Autowired private RefreshTokenRepository refreshTokens;
    @Autowired private OtpVerificationRepository otps;
    @Autowired private ContactRepository contacts;
    @Autowired private NotificationLogRepository notificationLogs;
    @Autowired private RequestCircleRepository circles;
    @Autowired private CircleResponseRepository circleResponses;

    private RetentionProperties props;
    private DataRetentionScheduler scheduler;

    @BeforeEach
    void setUp() {
        SchedulerTestData.clean(jdbc);
        props = new RetentionProperties();
        scheduler = new DataRetentionScheduler(jdbc, props, guard, CLOCK);
    }

    private static Instant ago(Duration d) {
        return NOW.minus(d);
    }

    private static LocalDateTime localAgo(Duration d) {
        return LocalDateTime.ofInstant(NOW.minus(d), ZoneId.systemDefault());
    }

    private static LocalDateTime localAhead(Duration d) {
        return LocalDateTime.ofInstant(NOW.plus(d), ZoneId.systemDefault());
    }

    // ---------------------------------------------------------------- refresh tokens

    @Test
    @DisplayName("Refresh tokens are deleted one day after they expired, not before")
    void refreshTokens() {
        User u = SchedulerTestData.user(users, Role.REQUESTER);
        save(u, "old", ago(Duration.ofDays(2)));
        save(u, "justExpired", ago(Duration.ofHours(2)));
        save(u, "valid", NOW.plus(Duration.ofDays(5)));

        assertEquals(1, scheduler.purgeExpiredRefreshTokens());

        assertEquals(2, refreshTokens.count());
        assertTrue(refreshTokens.findByTokenHash("justExpired").isPresent());
        assertTrue(refreshTokens.findByTokenHash("valid").isPresent());
        assertTrue(refreshTokens.findByTokenHash("old").isEmpty());
    }

    private void save(User u, String hash, Instant expiresAt) {
        RefreshToken t = new RefreshToken();
        t.setUser(u);
        t.setTokenHash(hash);
        t.setExpiresAt(expiresAt);
        refreshTokens.save(t);
    }

    // ---------------------------------------------------------------- OTP

    @Test
    @DisplayName("OTP rows are purged after a day")
    void otp() {
        otp("0771111111", localAgo(Duration.ofDays(2)));
        otp("0772222222", localAgo(Duration.ofHours(1)));

        assertEquals(1, scheduler.purgeOtpVerifications());

        assertTrue(otps.findTopByPhoneNumberOrderByCreatedAtDesc("0771111111").isEmpty());
        assertTrue(otps.findTopByPhoneNumberOrderByCreatedAtDesc("0772222222").isPresent());
    }

    private void otp(String phone, LocalDateTime createdAt) {
        OtpVerification o = new OtpVerification();
        o.setPhoneNumber(phone);
        o.setOtpCode("123456");
        o.setExpiresAt(createdAt.plusMinutes(10));
        o.setCreatedAt(createdAt);
        otps.save(o);
    }

    // ---------------------------------------------------------------- contact messages

    @Test
    @DisplayName("Handled messages go after 180 days, unhandled ones after 365, newer ones stay")
    void contactMessages() {
        contact("handled-old", ContactStatus.HANDLED, ago(Duration.ofDays(300)), ago(Duration.ofDays(200)));
        contact("handled-recent", ContactStatus.HANDLED, ago(Duration.ofDays(300)), ago(Duration.ofDays(100)));
        contact("new-ancient", ContactStatus.NEW, ago(Duration.ofDays(400)), null);
        contact("new-recent", ContactStatus.NEW, ago(Duration.ofDays(200)), null);

        assertEquals(2, scheduler.purgeContactMessages());

        var remaining = contacts.findAll().stream().map(ContactMessage::getSubject).sorted().toList();
        assertEquals(java.util.List.of("handled-recent", "new-recent"), remaining);
    }

    private void contact(String subject, ContactStatus status, Instant created, Instant handled) {
        ContactMessage m = new ContactMessage();
        m.setName("Someone");
        m.setEmail("someone@example.com");
        m.setSubject(subject);
        m.setMessage("a message that is long enough");
        m.setStatus(status);
        m.setCreatedAt(ZonedDateTime.ofInstant(created, ZoneOffset.UTC));
        if (handled != null) {
            m.setHandledAt(ZonedDateTime.ofInstant(handled, ZoneOffset.UTC));
        }
        contacts.save(m);
    }

    // ---------------------------------------------------------------- notification logs

    @Test
    @DisplayName("Notification logs lose phone and text after 90 days but keep the delivery record")
    void notificationLogs() {
        User owner = SchedulerTestData.user(users, Role.REQUESTER);
        DonorProfile donor = SchedulerTestData.donor(users, donors);
        BloodRequest req = SchedulerTestData.request(requests, owner, RequestStatus.OPEN, localAhead(Duration.ofDays(1)));
        NotificationLog old = log(donor, req, localAgo(Duration.ofDays(120)));
        NotificationLog recent = log(donor, req, localAgo(Duration.ofDays(10)));

        assertEquals(1, scheduler.scrubNotificationLogs());

        NotificationLog scrubbed = notificationLogs.findById(old.getId()).orElseThrow();
        assertNull(scrubbed.getRecipient());
        assertNull(scrubbed.getMessageBody());
        assertEquals(NotificationDeliveryStatus.SENT, scrubbed.getStatus(), "the delivery record itself stays");
        NotificationLog kept = notificationLogs.findById(recent.getId()).orElseThrow();
        assertEquals("+94771234567", kept.getRecipient());
        assertNotNull(kept.getMessageBody());
        assertEquals(0, scheduler.scrubNotificationLogs(), "idempotent");
    }

    private NotificationLog log(DonorProfile donor, BloodRequest req, LocalDateTime sentAt) {
        NotificationLog l = new NotificationLog(donor, req, NotificationChannel.SMS, NotificationDeliveryStatus.SENT,
                "+94771234567", "Urgent: O+ needed");
        l.setSentAt(sentAt);
        return notificationLogs.save(l);
    }

    // ---------------------------------------------------------------- circle pledges

    @Test
    @DisplayName("Guest pledge data is scrubbed 30 days after the request closed (or the circle expired)")
    void circleResponses() {
        User owner = SchedulerTestData.user(users, Role.REQUESTER);
        CircleResponse closedLongAgo = pledge(circle(owner, RequestStatus.CANCELLED, localAgo(Duration.ofDays(40))), "Kasun");
        CircleResponse closedRecently = pledge(circle(owner, RequestStatus.CANCELLED, localAgo(Duration.ofDays(10))), "Nimali");
        CircleResponse stillOpen = pledge(circle(owner, RequestStatus.OPEN, localAhead(Duration.ofDays(1))), "Ruwan");
        CircleResponse stuckMatched = pledge(circle(owner, RequestStatus.MATCHED, localAgo(Duration.ofDays(45))), "Dilini");

        assertEquals(2, scheduler.scrubCircleResponses());

        for (CircleResponse r : new CircleResponse[] {closedLongAgo, stuckMatched}) {
            CircleResponse s = circleResponses.findById(r.getId()).orElseThrow();
            assertEquals(DataRetentionScheduler.SCRUBBED_NAME, s.getResponderName());
            assertEquals("", s.getResponderPhone());
            assertNull(s.getNotes());
            assertNotNull(s.getScrubbedAt());
            assertEquals("O+", s.getBloodType(), "non-identifying data stays");
        }
        assertEquals("Nimali", circleResponses.findById(closedRecently.getId()).orElseThrow().getResponderName());
        assertEquals("Ruwan", circleResponses.findById(stillOpen.getId()).orElseThrow().getResponderName());
        assertEquals(0, scheduler.scrubCircleResponses(), "idempotent");
    }

    private RequestCircle circle(User owner, RequestStatus status, LocalDateTime requestExpiresAt) {
        BloodRequest req = SchedulerTestData.request(requests, owner, status, requestExpiresAt);
        return circles.save(new RequestCircle(req, java.util.UUID.randomUUID().toString().replace("-", "")
                + java.util.UUID.randomUUID().toString().replace("-", ""), null, requestExpiresAt));
    }

    private CircleResponse pledge(RequestCircle circle, String name) {
        return circleResponses.save(new CircleResponse(circle, name, "+94771234567", "O+", "after 5pm"));
    }

    // ---------------------------------------------------------------- requests

    @Test
    @DisplayName("Only the configured request statuses are purged, 30 days after expiry")
    void requests() {
        User owner = SchedulerTestData.user(users, Role.REQUESTER);
        BloodRequest expiredOld = SchedulerTestData.request(requests, owner, RequestStatus.EXPIRED, localAgo(Duration.ofDays(40)));
        BloodRequest cancelledOld = SchedulerTestData.request(requests, owner, RequestStatus.CANCELLED, localAgo(Duration.ofDays(40)));
        BloodRequest expiredRecent = SchedulerTestData.request(requests, owner, RequestStatus.EXPIRED, localAgo(Duration.ofDays(10)));
        BloodRequest fulfilledOld = SchedulerTestData.request(requests, owner, RequestStatus.FULFILLED, localAgo(Duration.ofDays(400)));
        BloodRequest matchedOld = SchedulerTestData.request(requests, owner, RequestStatus.MATCHED, localAgo(Duration.ofDays(400)));

        assertEquals(2, scheduler.purgeRequests());
        assertFalse(requests.existsById(expiredOld.getId()));
        assertFalse(requests.existsById(cancelledOld.getId()));
        assertTrue(requests.existsById(expiredRecent.getId()));
        assertTrue(requests.existsById(fulfilledOld.getId()));
        assertTrue(requests.existsById(matchedOld.getId()), "MATCHED is only purged when the lifecycle owner opts in");

        props.setRequestPurgeStatuses(EnumSet.of(RequestStatus.EXPIRED, RequestStatus.CANCELLED, RequestStatus.MATCHED));
        assertEquals(1, scheduler.purgeRequests());
        assertFalse(requests.existsById(matchedOld.getId()));
        assertTrue(requests.existsById(fulfilledOld.getId()));
    }

    // ---------------------------------------------------------------- whole run

    @Test
    @DisplayName("A purge that fails does not stop the others")
    void oneFailureDoesNotStopTheRun() {
        User u = SchedulerTestData.user(users, Role.REQUESTER);
        save(u, "old", ago(Duration.ofDays(3)));
        DataRetentionScheduler spied = spy(scheduler);
        doThrow(new IllegalStateException("boom")).when(spied).purgeContactMessages();

        spied.runAll();

        assertEquals(0, refreshTokens.count(), "refresh tokens were still purged");
    }

    @Test
    @DisplayName("Disabled purges (0 days) do nothing")
    void zeroDisables() {
        props.setContactHandledDays(0);
        props.setContactUnhandledDays(0);
        props.setNotificationLogScrubDays(0);
        props.setCircleResponseScrubDays(0);
        props.setRequestPurgeDays(0);
        contact("handled-old", ContactStatus.HANDLED, ago(Duration.ofDays(900)), ago(Duration.ofDays(800)));
        assertEquals(0, scheduler.purgeContactMessages());
        assertEquals(0, scheduler.scrubNotificationLogs());
        assertEquals(0, scheduler.scrubCircleResponses());
        assertEquals(0, scheduler.purgeRequests());
        assertEquals(1, contacts.count());
    }
}
