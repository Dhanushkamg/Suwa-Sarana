package com.suwasarana.api.notification.sms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/** Chooses the SMS provider from {@code app.sms.provider} (env SMS_PROVIDER): {@code notifylk} or {@code logging}. */
@Configuration
public class SmsConfig {

    private static final Logger log = LoggerFactory.getLogger(SmsConfig.class);

    @Bean
    public SmsProvider smsProvider(SmsProperties properties, Environment environment) {
        String provider = properties.getProvider() == null ? "logging" : properties.getProvider().trim().toLowerCase();
        boolean devLike = environment.acceptsProfiles(Profiles.of("dev", "local", "test"));

        if ("notifylk".equals(provider)) {
            SmsProperties.NotifyLk cfg = properties.getNotifylk();
            if (cfg.getUserId().isBlank() || cfg.getApiKey().isBlank()) {
                log.error("SMS_PROVIDER=notifylk but NOTIFYLK_USER_ID / NOTIFYLK_API_KEY are empty: every SMS will fail");
            }
            log.info("SMS provider: Notify.lk");
            return new NotifyLkSmsProvider(cfg);
        }
        if (!devLike) {
            log.warn("SMS provider is 'logging': NO SMS WILL BE SENT. Set SMS_PROVIDER=notifylk with NOTIFYLK_* credentials.");
        }
        return new LoggingSmsProvider();
    }
}
