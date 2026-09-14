package com.suwasarana.api.scheduler;

import com.suwasarana.api.donor.DonorProfile;
import com.suwasarana.api.notification.NotificationMessage;
import com.suwasarana.api.notification.NotificationService;
import com.suwasarana.api.slot.BookingStatus;
import com.suwasarana.api.slot.DonationSlot;
import com.suwasarana.api.slot.DonationSlotRepository;
import com.suwasarana.api.slot.SlotBooking;
import com.suwasarana.api.slot.SlotBookingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Sends pre-appointment reminders to donors 24 hours and 2 hours before their booked slot.
 * Uses the existing @EnableScheduling infrastructure (already active via DataRetentionScheduler).
 * Delegates notifications through the NotificationService's generic overload,
 * which cascades SSE → SMS exactly like emergency blood request notifications.
 */
@Service
public class SlotReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(SlotReminderScheduler.class);

    @Autowired private DonationSlotRepository slotRepository;
    @Autowired private SlotBookingRepository bookingRepository;
    @Autowired private NotificationService notificationService;

    /**
     * 24-hour reminder.
     * Runs every hour; targets slots starting 23h–25h from now to avoid double-sending.
     */
    @Scheduled(cron = "0 0 * * * *")
    public void send24HourReminders() {
        LocalDateTime from = LocalDateTime.now().plusHours(23);
        LocalDateTime to   = LocalDateTime.now().plusHours(25);
        sendReminders(from, to, "24 hours");
    }

    /**
     * 2-hour reminder.
     * Runs every 15 minutes; targets slots starting 1h45m–2h15m from now.
     */
    @Scheduled(cron = "0 0/15 * * * *")
    public void send2HourReminders() {
        LocalDateTime from = LocalDateTime.now().plusMinutes(105);
        LocalDateTime to   = LocalDateTime.now().plusMinutes(135);
        sendReminders(from, to, "2 hours");
    }

    private void sendReminders(LocalDateTime from, LocalDateTime to, String windowLabel) {
        List<DonationSlot> upcomingSlots = slotRepository.findSlotsStartingBetween(from, to);
        if (upcomingSlots.isEmpty()) return;

        log.info("SlotReminderScheduler: {} slot(s) starting in ~{} — dispatching reminders.", upcomingSlots.size(), windowLabel);

        for (DonationSlot slot : upcomingSlots) {
            List<SlotBooking> bookings = bookingRepository.findBySlotIdAndStatus(slot.getId(), BookingStatus.BOOKED);
            for (SlotBooking booking : bookings) {
                DonorProfile donor = booking.getDonor();
                NotificationMessage msg = new NotificationMessage(
                        "Donation Reminder — " + windowLabel,
                        "Your blood donation slot at " + slot.resolveHostName()
                                + " starts at " + slot.getStartTime()
                                + ". Please bring your ID. Thank you!",
                        "SLOT_REMINDER",
                        booking.getId()
                );
                try {
                    notificationService.notify(donor, msg);
                } catch (Exception e) {
                    log.warn("Failed to send {} reminder for booking {}: {}", windowLabel, booking.getId(), e.getMessage());
                }
            }
        }
    }
}
