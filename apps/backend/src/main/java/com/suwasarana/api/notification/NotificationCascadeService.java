package com.suwasarana.api.notification;

import com.suwasarana.api.donor.DonorProfile;
import com.suwasarana.api.notification.sms.SmsNotificationService;
import com.suwasarana.api.request.BloodRequest;
import com.suwasarana.api.request.Urgency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;

@Service
public class NotificationCascadeService implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationCascadeService.class);

    private final SseNotificationService sseNotificationService;
    private final SmsNotificationService smsNotificationService;
    private final NotificationLogRepository notificationLogRepository;

    @Autowired
    public NotificationCascadeService(SseNotificationService sseNotificationService,
                                    SmsNotificationService smsNotificationService,
                                    NotificationLogRepository notificationLogRepository) {
        this.sseNotificationService = sseNotificationService;
        this.smsNotificationService = smsNotificationService;
        this.notificationLogRepository = notificationLogRepository;
    }

    @Override
    @Transactional
    public void notify(DonorProfile donor, BloodRequest request) {
        sendNotification(donor, request);
    }

    /**
     * Generic overload for pre-built notifications (slot confirmations, reminders, etc.).
     * Reuses the same quiet-hours check and SSE → SMS cascade as the BloodRequest path.
     */
    @Override
    @Transactional
    public void notify(DonorProfile donor, NotificationMessage message) {
        if (isInQuietHours(donor)) {
            log.info("Skipping generic notification for donor {} due to quiet hours.", donor.getId());
            return;
        }

        Long userId = donor.getUser() != null ? donor.getUser().getId() : null;
        boolean sseSent = false;

        if (userId != null) {
            sseSent = sseNotificationService.sendNotificationIfConnected(userId, message);
        }

        if (sseSent) {
            log.info("Generic notification sent via SSE to user {}", userId);
        } else {
            log.info("User {} not on SSE for generic notification; falling back to SMS.", userId);
            smsNotificationService.sendSmsNotification(donor, message);
        }
    }

    @Transactional
    public void sendNotification(DonorProfile donor, BloodRequest request) {
        if (isInQuietHours(donor) && request.getUrgency() != Urgency.CRITICAL) {
            log.info("Skipping notification for donor {} due to quiet hours. Urgency: {}", donor.getId(), request.getUrgency());
            saveLog(donor, request, NotificationChannel.SSE, NotificationDeliveryStatus.SKIPPED_QUIET_HOURS,
                    donor.getUser() != null ? donor.getUser().getEmail() : null,
                    "Skipped due to donor quiet hours.");
            return;
        }

        NotificationMessage message = new NotificationMessage(
                "Urgent Blood Request!",
                "A patient needs " + request.getPatientBloodType() + " blood at " + request.getHospitalName() + " (" + request.getDistrict() + ").",
                "NEW_MATCH",
                request.getId()
        );

        Long userId = donor.getUser() != null ? donor.getUser().getId() : null;
        boolean sseSent = false;

        if (userId != null) {
            sseSent = sseNotificationService.sendNotificationIfConnected(userId, message);
        }

        if (sseSent) {
            log.info("Notification successfully sent via SSE to user {}", userId);
            saveLog(donor, request, NotificationChannel.SSE, NotificationDeliveryStatus.SENT,
                    donor.getUser().getEmail(), message.getBody());
        } else {
            log.info("User {} not connected via SSE or SSE transmission failed. Cascading fallback to SMS.", userId);
            String formattedSms = smsNotificationService.formatSmsMessage(request);
            boolean smsSent = smsNotificationService.sendSmsNotification(donor, request);

            if (smsSent) {
                log.info("SMS fallback notification sent successfully to donor {}", donor.getId());
                saveLog(donor, request, NotificationChannel.SMS, NotificationDeliveryStatus.SENT,
                        donor.getUser() != null ? donor.getUser().getPhoneNumber() : null, formattedSms);
            } else {
                log.warn("SMS fallback notification failed for donor {}", donor.getId());
                saveLog(donor, request, NotificationChannel.SMS, NotificationDeliveryStatus.FAILED,
                        donor.getUser() != null ? donor.getUser().getPhoneNumber() : null, formattedSms);
            }
        }
    }

    public boolean isInQuietHours(DonorProfile donor) {
        LocalTime start = donor.getQuietHoursStart();
        LocalTime end = donor.getQuietHoursEnd();

        if (start == null || end == null) return false;

        return isTimeInQuietHours(LocalTime.now(), start, end);
    }

    public boolean isTimeInQuietHours(LocalTime now, LocalTime start, LocalTime end) {
        if (start.isBefore(end)) {
            return !now.isBefore(start) && now.isBefore(end);
        } else {
            // crosses midnight (e.g., 22:00 to 07:00)
            return !now.isBefore(start) || now.isBefore(end);
        }
    }

    private void saveLog(DonorProfile donor, BloodRequest request, NotificationChannel channel,
                         NotificationDeliveryStatus status, String recipient, String messageBody) {
        try {
            NotificationLog logEntry = new NotificationLog(donor, request, channel, status, recipient, messageBody);
            notificationLogRepository.save(logEntry);
        } catch (Exception e) {
            log.error("Failed to persist notification audit log for donor {} / request {}: {}",
                    donor.getId(), request.getId(), e.getMessage());
        }
    }
}
