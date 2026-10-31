package com.suwasarana.api.notification.sms;

import com.suwasarana.api.user.PhoneNumbers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Development fallback: nothing is sent. Only the masked recipient and the message LENGTH are logged, never the text
 * (it carries OTP codes and alert details). It returns {@code true} because the "dispatch" itself cannot fail; production
 * must use a real provider (SMS_PROVIDER=notifylk) and a warning is raised at startup when it does not.
 */
public class LoggingSmsProvider implements SmsProvider {

    private static final Logger log = LoggerFactory.getLogger(LoggingSmsProvider.class);

    @Override
    public boolean sendSms(String recipientPhone, String message) {
        if (recipientPhone == null || recipientPhone.isBlank()) {
            return false;
        }
        log.info("[SMS not sent - logging provider] to={} ({} characters)", PhoneNumbers.mask(recipientPhone),
                message == null ? 0 : message.length());
        return true;
    }
}
