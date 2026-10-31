package com.suwasarana.api.scheduler;

import com.suwasarana.api.notification.NotificationMessage;
import com.suwasarana.api.notification.NotificationService;
import com.suwasarana.api.slot.SlotBooking;
import com.suwasarana.api.slot.SlotBookingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Sends pre-appointment reminders to donors 24 hours and 2 hours before their booked slot.
 *
 * <p>Each booking is reminded <b>once per window</b>: the booking row carries {@code reminder_24h_sent_at} /
 * {@code reminder_2h_sent_at}, and a reminder is only sent by the run that manages to set the flag
 * ({@code UPDATE ... WHERE flag IS NULL}). The two windows do not overlap (slots starting 22-24 h ahead, and 1-2 h
 * ahead), every window is longer than the interval between runs so a missed run is caught by the next one, and a
 * failed send rolls its flag back so the next run retries. A booking row that is cancelled and booked again gets a
 * new {@code booked_at}, which makes an older flag count as "not sent". All times come from the injected
 * {@link Clock} (Asia/Colombo by default). A PostgreSQL advisory lock keeps several instances from running a job at
 * the same time.
 */
@Service
public class SlotReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(SlotReminderScheduler.class);
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** One reminder window: slots that start between {@code from} and {@code to} from now are due. */
    enum Window {
        H24("24 hours", "reminder_24h_sent_at", Duration.ofHours(22), Duration.ofHours(24)),
        H2("2 hours", "reminder_2h_sent_at", Duration.ofHours(1), Duration.ofHours(2));

        final String label;
        final String column;
        final Duration from;
        final Duration to;

        Window(String label, String column, Duration from, Duration to) {
            this.label = label;
            this.column = column;
            this.from = from;
            this.to = to;
        }
    }

    private final JdbcTemplate jdbc;
    private final SlotBookingRepository bookingRepository;
    private final NotificationService notificationService;
    private final SchedulerGuard guard;
    private final TransactionTemplate tx;
    private final Clock clock;

    @Autowired
    public SlotReminderScheduler(JdbcTemplate jdbc, SlotBookingRepository bookingRepository, NotificationService notificationService,
                          SchedulerGuard guard, PlatformTransactionManager transactionManager, Clock clock) {
        this.jdbc = jdbc;
        this.bookingRepository = bookingRepository;
        this.notificationService = notificationService;
        this.guard = guard;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /** Hourly; slots starting 22-24 h from now. */
    @Scheduled(cron = "0 0 * * * *", zone = "Asia/Colombo")
    public void send24HourReminders() {
        guard.runExclusively("slot-reminder-24h", () -> sendReminders(Window.H24));
    }

    /** Every 15 minutes; slots starting 1-2 h from now. */
    @Scheduled(cron = "0 0/15 * * * *", zone = "Asia/Colombo")
    public void send2HourReminders() {
        guard.runExclusively("slot-reminder-2h", () -> sendReminders(Window.H2));
    }

    /** @return the number of reminders sent by this run */
    int sendReminders(Window window) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Long> due = jdbc.queryForList(
                "SELECT b.id FROM slot_bookings b JOIN donation_slots s ON s.id = b.slot_id "
                        + "WHERE b.status = 'BOOKED' AND s.status IN ('OPEN', 'FULL') "
                        + "AND (b." + window.column + " IS NULL OR b." + window.column + " < b.booked_at) "
                        + "AND s.start_time > ? AND s.start_time <= ? ORDER BY b.id",
                Long.class, now.plus(window.from), now.plus(window.to));
        if (due.isEmpty()) {
            return 0;
        }
        log.info("SlotReminderScheduler: {} booking(s) due a {} reminder", due.size(), window.label);

        int sent = 0;
        for (Long bookingId : due) {
            try {
                Integer n = tx.execute(status -> remindOne(bookingId, window));
                sent += n == null ? 0 : n;
            } catch (RuntimeException e) {
                log.warn("Failed to send {} reminder for booking {} ({})", window.label, bookingId, e.getClass().getSimpleName());
            }
        }
        return sent;
    }

    private int remindOne(Long bookingId, Window window) {
        // Claim the reminder. Only one run can flip the flag from "not sent"; everybody else gets 0 rows.
        int claimed = jdbc.update(
                "UPDATE slot_bookings SET " + window.column + " = ? WHERE id = ? AND status = 'BOOKED' "
                        + "AND (" + window.column + " IS NULL OR " + window.column + " < booked_at)",
                Timestamp.from(clock.instant()), bookingId);
        if (claimed == 0) {
            return 0;
        }
        SlotBooking booking = bookingRepository.findById(bookingId).orElse(null);
        if (booking == null) {
            return 0;
        }
        var slot = booking.getSlot();
        NotificationMessage msg = new NotificationMessage(
                "Donation Reminder - " + window.label,
                "Your blood donation slot at " + slot.resolveHostName()
                        + " starts at " + slot.getStartTime().format(WHEN)
                        + ". Please bring your ID. Thank you!",
                "SLOT_REMINDER",
                booking.getId());
        notificationService.notify(booking.getDonor(), msg); // an exception rolls the claim back
        return 1;
    }
}
