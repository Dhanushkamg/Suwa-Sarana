package com.suwasarana.api.notification.sms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
@ConditionalOnMissingBean(name = "productionSmsProvider")
public class LoggingSmsProvider implements SmsProvider {

    private static final Logger log = LoggerFactory.getLogger(LoggingSmsProvider.class);

    @Value("${app.sms.provider-api-key:mock_key}")
    private String apiKey;

    @Override
    public boolean sendSms(String recipientPhone, String message) {
        log.info(">>> [SMS STUB DISPATCH] To: {} | Key configured: {} | Message: {}",
                recipientPhone,
                (apiKey != null && !apiKey.isEmpty() ? "YES" : "NO"),
                message);
        return true;
    }
}
