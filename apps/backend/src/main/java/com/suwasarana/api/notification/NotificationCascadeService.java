package com.suwasarana.api.notification;

import com.suwasarana.api.donor.DonorProfile;
import com.suwasarana.api.request.BloodRequest;
import com.suwasarana.api.request.Urgency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalTime;

@Service
public class NotificationCascadeService {

    private static final Logger log = LoggerFactory.getLogger(NotificationCascadeService.class);

    @Autowired
    private NotificationService sseNotificationService;

    public void sendNotification(DonorProfile donor, BloodRequest request) {
        if (isInQuietHours(donor) && request.getUrgency() != Urgency.CRITICAL) {
            log.info("Skipping notification for donor {} due to quiet hours. Urgency: {}", donor.getId(), request.getUrgency());
            return;
        }

        NotificationMessage message = new NotificationMessage(
                "Urgent Blood Request!",
                "A patient needs " + request.getPatientBloodType() + " blood nearby.",
                "NEW_MATCH",
                request.getId()
        );

        boolean sseSent = sseNotificationService.sendNotificationIfConnected(donor.getUser().getId(), message);

        if (!sseSent) {
            log.info("User {} not connected via SSE. Falling back to SMS notification.", donor.getUser().getId());
            sendSmsMock(donor.getUser().getPhoneNumber(), message.getBody());
        }
    }

    private boolean isInQuietHours(DonorProfile donor) {
        LocalTime start = donor.getQuietHoursStart();
        LocalTime end = donor.getQuietHoursEnd();
        
        if (start == null || end == null) return false;

        LocalTime now = LocalTime.now();

        if (start.isBefore(end)) {
            return !now.isBefore(start) && now.isBefore(end);
        } else {
            // crosses midnight
            return !now.isBefore(start) || now.isBefore(end);
        }
    }

    private void sendSmsMock(String phoneNumber, String message) {
        log.info(">>> [MOCK SMS to {}]: {}", phoneNumber, message);
    }
}
