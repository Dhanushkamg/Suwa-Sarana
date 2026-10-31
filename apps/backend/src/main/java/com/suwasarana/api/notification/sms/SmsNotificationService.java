package com.suwasarana.api.notification.sms;

import com.suwasarana.api.donor.DonorProfile;
import com.suwasarana.api.notification.NotificationMessage;
import com.suwasarana.api.request.BloodRequest;
import com.suwasarana.api.user.PhoneNumbers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SmsNotificationService {

    private static final Logger log = LoggerFactory.getLogger(SmsNotificationService.class);

    private final SmsProvider smsProvider;

    /** Frontend base URL (app.frontend-url / FRONTEND_URL); the alert links to {base}/dashboard/donor/matches. */
    @org.springframework.beans.factory.annotation.Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl = "http://localhost:3000";

    @Autowired
    public SmsNotificationService(SmsProvider smsProvider) {
        this.smsProvider = smsProvider;
    }

    /** @return true only when the provider confirmed the message was accepted for delivery. */
    public boolean sendSmsNotification(DonorProfile donor, BloodRequest request) {
        if (donor.getUser() == null || donor.getUser().getPhoneNumber() == null || donor.getUser().getPhoneNumber().isBlank()) {
            log.warn("Cannot send SMS: donor {} has no registered phone number.", donor.getId());
            return false;
        }

        String phone = donor.getUser().getPhoneNumber();
        String message = formatSmsMessage(request);

        log.info("Dispatching SMS alert to donor {} ({}) for request {}", donor.getId(), PhoneNumbers.mask(phone), request.getId());
        return smsProvider.sendSms(phone, message);
    }

    /** @return true only when the provider confirmed the message was accepted for delivery. */
    public boolean sendSmsNotification(DonorProfile donor, NotificationMessage message) {
        if (donor.getUser() == null || donor.getUser().getPhoneNumber() == null || donor.getUser().getPhoneNumber().isBlank()) {
            log.warn("Cannot send SMS: donor {} has no registered phone number.", donor.getId());
            return false;
        }

        String phone = donor.getUser().getPhoneNumber();
        return smsProvider.sendSms(phone, message.getTitle() + ": " + message.getBody());
    }

    public String formatSmsMessage(BloodRequest request) {
        return String.format("[Suwa Sarana Emergency Alert] Urgent %s blood needed at %s (%s). " +
                        "Urgency: %s. Please log in to accept: %s/dashboard/donor/matches",
                request.getPatientBloodType(),
                request.getHospitalName(),
                request.getDistrict(),
                request.getUrgency(),
                frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl);
    }
}
