package com.suwasarana.api.notification;

import com.suwasarana.api.donor.DonorProfile;
import com.suwasarana.api.notification.dto.NotificationDto;
import com.suwasarana.api.notification.sms.SmsNotificationService;
import com.suwasarana.api.request.BloodRequest;
import com.suwasarana.api.request.Urgency;
import com.suwasarana.api.shared.AfterCommit;
import com.suwasarana.api.shared.AppClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Delivers a donor alert: inbox row first (always), then live SSE, then SMS only if no stream took it.
 *
 * <p>Order of events for one alert: (1) the inbox row is written inside the current transaction; (2) once that
 * transaction commits, the alert is pushed to every open stream of the user; (3) if no stream accepted it, an SMS
 * is sent in the user's preferred language (en/si/ta). Quiet hours suppress (2) and (3) for non-CRITICAL alerts, but
 * the inbox row is still written so the donor sees it when they next open the app.
 */
@Service
public class NotificationCascadeService implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationCascadeService.class);

    private final SseNotificationService sseNotificationService;
    private final SmsNotificationService smsNotificationService;
    private final NotificationLogRepository notificationLogRepository;
    private final NotificationInboxService inbox;
    private final UserLanguageLookup languageLookup;
    private final AppClock clock;

    @Autowired
    public NotificationCascadeService(SseNotificationService sseNotificationService,
                                      SmsNotificationService smsNotificationService,
                                      NotificationLogRepository notificationLogRepository,
                                      NotificationInboxService inbox,
                                      UserLanguageLookup languageLookup,
                                      AppClock clock) {
        this.sseNotificationService = sseNotificationService;
        this.smsNotificationService = smsNotificationService;
        this.notificationLogRepository = notificationLogRepository;
        this.inbox = inbox;
        this.languageLookup = languageLookup;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void notify(DonorProfile donor, BloodRequest request) {
        sendNotification(donor, request);
    }

    /**
     * Generic overload for pre-built notifications (slot confirmations, reminders, ...).
     * Same inbox -> SSE -> SMS cascade and the same quiet-hours rule as the blood-request path.
     */
    @Override
    @Transactional
    public void notify(DonorProfile donor, NotificationMessage message) {
        Long userId = donor.getUser() != null ? donor.getUser().getId() : null;
        NotificationDto stored = userId == null ? null : inbox.persist(userId, message);

        if (isInQuietHours(donor)) {
            log.info("Not pushing generic notification to donor {} (quiet hours); it is in the inbox.", donor.getId());
            return;
        }

        String lang = languageLookup.languageOf(userId);
        AfterCommit.run(() -> {
            int reached = stored == null ? 0 : sseNotificationService.push(userId, stored);
            if (reached > 0) {
                log.info("Generic notification delivered over SSE to user {}", userId);
                return;
            }
            NotificationMessage sms = new NotificationMessage(
                    localisedTitle(message, lang), localisedBody(message, lang), message.getType(), message.getReferenceId());
            smsNotificationService.sendSmsNotification(donor, sms);
        });
    }

    @Transactional
    public void sendNotification(DonorProfile donor, BloodRequest request) {
        Long userId = donor.getUser() != null ? donor.getUser().getId() : null;
        String email = donor.getUser() != null ? donor.getUser().getEmail() : null;
        String phone = donor.getUser() != null ? donor.getUser().getPhoneNumber() : null;

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("requestId", request.getId());
        params.put("bloodType", request.getPatientBloodType());
        params.put("hospital", request.getHospitalName());
        params.put("district", request.getDistrict());
        params.put("urgency", request.getUrgency() == null ? null : request.getUrgency().name());
        params.put("units", request.getUnitsNeeded());

        NotificationDto stored = userId == null ? null
                : inbox.persist(userId, "NEW_MATCH", "notifications.match.title", "notifications.match.body", params, request.getId());

        if (isInQuietHours(donor) && request.getUrgency() != Urgency.CRITICAL) {
            log.info("Not pushing alert to donor {} (quiet hours, urgency {}); it is in the inbox.", donor.getId(), request.getUrgency());
            saveLog(donor, request, NotificationChannel.SSE, NotificationDeliveryStatus.SKIPPED_QUIET_HOURS, email,
                    "Skipped due to donor quiet hours.");
            return;
        }

        String lang = languageLookup.languageOf(userId);
        AfterCommit.run(() -> {
            int reached = stored == null ? 0 : sseNotificationService.push(userId, stored);
            if (reached > 0) {
                log.info("Alert delivered over SSE to user {} ({} stream(s))", userId, reached);
                saveLog(donor, request, NotificationChannel.SSE, NotificationDeliveryStatus.SENT, email, stored.getBody());
                return;
            }
            String body = matchSmsText(request, lang);
            NotificationMessage sms = new NotificationMessage(
                    NotificationTemplates.render("notifications.match.title", params, lang), body, "NEW_MATCH", request.getId());
            boolean sent = smsNotificationService.sendSmsNotification(donor, sms);
            saveLog(donor, request, NotificationChannel.SMS,
                    sent ? NotificationDeliveryStatus.SENT : NotificationDeliveryStatus.FAILED, phone, body);
        });
    }

    /** The localised SMS body for a donor alert: what is needed, where, urgency, and a prompt to open the app. */
    String matchSmsText(BloodRequest request, String lang) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("bloodType", request.getPatientBloodType());
        p.put("hospital", request.getHospitalName());
        p.put("district", request.getDistrict());
        p.put("urgency", NotificationTemplates.urgencyWord(request.getUrgency() == null ? null : request.getUrgency().name(), lang));
        return NotificationTemplates.render("notifications.match.body", p, lang) + " "
                + NotificationTemplates.render("notifications.sms.respond", p, lang);
    }

    private String localisedTitle(NotificationMessage m, String lang) {
        if (m.getTitleKey() != null) return NotificationTemplates.render(m.getTitleKey(), m.getParams(), lang);
        return m.getTitle();
    }

    private String localisedBody(NotificationMessage m, String lang) {
        if (m.getBodyKey() != null) return NotificationTemplates.render(m.getBodyKey(), m.getParams(), lang);
        return m.getBody();
    }

    public boolean isInQuietHours(DonorProfile donor) {
        LocalTime start = donor.getQuietHoursStart();
        LocalTime end = donor.getQuietHoursEnd();

        if (start == null || end == null) return false;

        return isTimeInQuietHours(clock.time(), start, end);
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
            logEntry.setSentAt(clock.now());
            notificationLogRepository.save(logEntry);
        } catch (Exception e) {
            log.error("Failed to persist notification audit log for donor {} / request {}: {}",
                    donor.getId(), request.getId(), e.getMessage());
        }
    }
}
