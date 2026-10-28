package com.suwasarana.api.notification.sms;

import com.suwasarana.api.user.PhoneNumbers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Development fallback: nothing is sent, the message is only written to the log. The recipient is always masked;
 * the message body (which carries OTP codes) is printed only when {@code logBody} is true (dev profiles or
 * SMS_LOG_BODY=true). It returns {@code true} because the "dispatch" itself cannot fail; production must use a real
 * provider (SMS_PROVIDER=notifylk) and a warning is raised at startup when it does not.
 */
public class LoggingSmsProvider implements SmsProvider {

    private static final Logger log = LoggerFactory.getLogger(LoggingSmsProvider.class);

    private final boolean logBody;

    public LoggingSmsProvider(boolean logBody) {
        this.logBody = logBody;
    }

    @Override
    public boolean sendSms(String recipientPhone, String message) {
        if (recipientPhone == null || recipientPhone.isBlank()) {
            return false;
        }
        if (logBody) {
            log.info("[SMS not sent - logging provider] to={} message={}", PhoneNumbers.mask(recipientPhone), message);
        } else {
            log.info("[SMS not sent - logging provider] to={} ({} characters)", PhoneNumbers.mask(recipientPhone),
                    message == null ? 0 : message.length());
        }
        return true;
    }
}
