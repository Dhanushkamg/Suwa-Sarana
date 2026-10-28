package com.suwasarana.api.scheduler;

import com.suwasarana.api.donor.DonorProfile;
import com.suwasarana.api.donor.DonorRepository;
import com.suwasarana.api.notification.NotificationMessage;
import com.suwasarana.api.notification.NotificationService;
import com.suwasarana.api.slot.BookingStatus;
import com.suwasarana.api.slot.DonationSlot;
import com.suwasarana.api.slot.DonationSlotRepository;
import com.suwasarana.api.slot.SlotBooking;
import com.suwasarana.api.slot.SlotBookingRepository;
import com.suwasarana.api.slot.SlotStatus;
import com.suwasarana.api.user.Role;
import com.suwasarana.api.user.User;
import com.suwasarana.api.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
class SlotReminderSchedulerTest {

    private static final Instant NOW = Instant.parse("2026-10-28T10:00:00Z");
    private static final ZoneId COLOMBO = ZoneId.of("Asia/Colombo");
    private static final Clock CLOCK = Clock.fixed(NOW, COLOMBO);
    private static final LocalDateTime LOCAL_NOW = LocalDateTime.now(CLOCK); // 15:30 Colombo

    @Autowired private JdbcTemplate jdbc;
    @Autowired private SchedulerGuard guard;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private UserRepository users;
    @Autowired private DonorRepository donors;
    @Autowired private DonationSlotRepository slots;
    @Autowired private SlotBookingRepository bookings;

    private NotificationService notifications;
    private SlotReminderScheduler scheduler;
    private User hospital;
    private DonorProfile donor;

    @BeforeEach
    void setUp() {
        // the reminder flags come from migration V41; the H2 test schema is generated from the entities, so add them here
        jdbc.execute("ALTER TABLE slot_bookings ADD COLUMN IF NOT EXISTS reminder_24h_sent_at TIMESTAMP WITH TIME ZONE");
        jdbc.execute("ALTER TABLE slot_bookings ADD COLUMN IF NOT EXISTS reminder_2h_sent_at TIMESTAMP WITH TIME ZONE");
        SchedulerTestData.clean(jdbc);
        notifications = mock(NotificationService.class);
        scheduler = new SlotReminderScheduler(jdbc, bookings, notifications, guard, transactionManager, CLOCK);
        hospital = SchedulerTestData.user(users, Role.HOSPITAL_REQUESTER);
        donor = SchedulerTestData.donor(users, donors);
    }

    private SlotBooking book(Duration startsIn, BookingStatus bookingStatus, SlotStatus slotStatus) {
        DonationSlot slot = new DonationSlot();
        slot.setHospitalUser(hospital);
        slot.setStartTime(LOCAL_NOW.plus(startsIn));
        slot.setEndTime(LOCAL_NOW.plus(startsIn).plusMinutes(30));
        slot.setCapacity(5);
        slot.setStatus(slotStatus);
        slot = slots.save(slot);

        SlotBooking b = new SlotBooking();
        b.setSlot(slot);
        b.setDonor(donor);
        b.setStatus(bookingStatus);
        b.setBookedAt(NOW.minus(Duration.ofDays(3)));
        return bookings.save(b);
    }

    private SlotBooking book(Duration startsIn) {
        return book(startsIn, BookingStatus.BOOKED, SlotStatus.OPEN);
    }

    private Object flag(Long bookingId, String column) {
        return jdbc.queryForList("SELECT " + column + " FROM slot_bookings WHERE id = ?", bookingId).get(0).values().iterator().next();
    }

    @Test
    @DisplayName("A booking 23 h away gets the 24 h reminder exactly once, however often the job runs")
    void reminder24h_isSentOnce() {
        SlotBooking b = book(Duration.ofHours(23));

        assertEquals(1, scheduler.sendReminders(SlotReminderScheduler.Window.H24));
        assertEquals(0, scheduler.sendReminders(SlotReminderScheduler.Window.H24));
        assertEquals(0, scheduler.sendReminders(SlotReminderScheduler.Window.H24));

        ArgumentCaptor<NotificationMessage> msg = ArgumentCaptor.forClass(NotificationMessage.class);
        verify(notifications, times(1)).notify(any(DonorProfile.class), msg.capture());
        assertTrue(msg.getValue().getTitle().contains("24 hours"));
        assertEquals("SLOT_REMINDER", msg.getValue().getType());
        assertEquals(b.getId(), msg.getValue().getReferenceId());
        assertNotNull(flag(b.getId(), "reminder_24h_sent_at"));
        assertNull(flag(b.getId(), "reminder_2h_sent_at"), "the other window is untouched");
    }

    @Test
    @DisplayName("The 2 h reminder is independent of the 24 h one and also sent once")
    void bothWindows() {
        SlotBooking day = book(Duration.ofHours(23));
        SlotBooking soon = book(Duration.ofMinutes(90));

        assertEquals(1, scheduler.sendReminders(SlotReminderScheduler.Window.H24));
        assertEquals(1, scheduler.sendReminders(SlotReminderScheduler.Window.H2));
        assertEquals(0, scheduler.sendReminders(SlotReminderScheduler.Window.H2));
        verify(notifications, times(2)).notify(any(DonorProfile.class), any(NotificationMessage.class));
        assertNotNull(flag(day.getId(), "reminder_24h_sent_at"));
        assertNotNull(flag(soon.getId(), "reminder_2h_sent_at"));
        assertNull(flag(soon.getId(), "reminder_24h_sent_at"));
    }

    @Test
    @DisplayName("The windows do not overlap: a slot 10 h away, 30 min away or 3 days away is reminded by neither job")
    void outsideWindows() {
        book(Duration.ofHours(10));
        book(Duration.ofMinutes(30));
        book(Duration.ofDays(3));
        book(Duration.ofHours(-1));

        assertEquals(0, scheduler.sendReminders(SlotReminderScheduler.Window.H24));
        assertEquals(0, scheduler.sendReminders(SlotReminderScheduler.Window.H2));
        verify(notifications, never()).notify(any(DonorProfile.class), any(NotificationMessage.class));
    }

    @Test
    @DisplayName("Cancelled bookings and cancelled slots are not reminded")
    void cancelledAreSkipped() {
        book(Duration.ofHours(23), BookingStatus.CANCELLED, SlotStatus.OPEN);
        book(Duration.ofHours(23), BookingStatus.BOOKED, SlotStatus.CANCELLED);
        assertEquals(0, scheduler.sendReminders(SlotReminderScheduler.Window.H24));
    }

    @Test
    @DisplayName("A failed send rolls the flag back so the next run retries")
    void failedSendIsRetried() {
        SlotBooking b = book(Duration.ofHours(23));
        doThrow(new IllegalStateException("sms down")).when(notifications).notify(any(DonorProfile.class), any(NotificationMessage.class));

        assertEquals(0, scheduler.sendReminders(SlotReminderScheduler.Window.H24));
        assertNull(flag(b.getId(), "reminder_24h_sent_at"));

        org.mockito.Mockito.reset(notifications);
        assertEquals(1, scheduler.sendReminders(SlotReminderScheduler.Window.H24));
        verify(notifications, times(1)).notify(any(DonorProfile.class), any(NotificationMessage.class));
    }

    @Test
    @DisplayName("Two runs at the same moment (two instances) still send one reminder")
    void concurrentRunsSendOnce() throws Exception {
        for (int i = 0; i < 5; i++) {
            book(Duration.ofHours(23));
        }
        SlotReminderScheduler other = new SlotReminderScheduler(jdbc, bookings, notifications, guard, transactionManager, CLOCK);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);
        Future<Integer> a = pool.submit(() -> { go.await(); return scheduler.sendReminders(SlotReminderScheduler.Window.H24); });
        Future<Integer> b = pool.submit(() -> { go.await(); return other.sendReminders(SlotReminderScheduler.Window.H24); });
        go.countDown();
        int total = a.get() + b.get();
        pool.shutdown();

        assertEquals(5, total);
        verify(notifications, times(5)).notify(any(DonorProfile.class), any(NotificationMessage.class));
    }

    @Test
    @DisplayName("A booking row that was cancelled and booked again (newer booked_at) is reminded again")
    void rebookedBookingIsRemindedAgain() {
        SlotBooking b = book(Duration.ofHours(23));
        assertEquals(1, scheduler.sendReminders(SlotReminderScheduler.Window.H24));

        // the donor cancels and re-books the same slot: SlotBookingService re-stamps booked_at
        jdbc.update("UPDATE slot_bookings SET booked_at = ? WHERE id = ?", java.sql.Timestamp.from(NOW.plusSeconds(60)), b.getId());
        assertEquals(1, scheduler.sendReminders(SlotReminderScheduler.Window.H24));
        verify(notifications, times(2)).notify(any(DonorProfile.class), any(NotificationMessage.class));
    }

    @Test
    @DisplayName("Reminder times use the Colombo clock, not the server's zone")
    void usesInjectedClock() {
        // 23 h after "now" in Colombo is inside the window; the same wall-clock value read in UTC would be 5.5 h off
        SlotBooking b = book(Duration.ofHours(23));
        Clock utc = Clock.fixed(NOW, ZoneId.of("UTC")); // 10:00 local wall clock instead of 15:30
        SlotReminderScheduler utcScheduler = new SlotReminderScheduler(jdbc, bookings, notifications, guard, transactionManager, utc);
        assertEquals(0, utcScheduler.sendReminders(SlotReminderScheduler.Window.H24),
                "with a UTC clock the slot is 28.5 h away: not due yet");
        assertEquals(1, scheduler.sendReminders(SlotReminderScheduler.Window.H24));
        assertNotNull(flag(b.getId(), "reminder_24h_sent_at"));
    }
}
