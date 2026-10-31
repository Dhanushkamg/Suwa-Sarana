package com.suwasarana.api.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/** Chooses the email sender from {@code app.email.provider} (env EMAIL_PROVIDER): {@code smtp} or {@code logging}. */
@Configuration
public class EmailConfig {

    private static final Logger log = LoggerFactory.getLogger(EmailConfig.class);

    @Bean
    public EmailSender emailSender(EmailProperties properties, Environment environment) {
        String provider = properties.getProvider() == null ? "logging" : properties.getProvider().trim().toLowerCase();
        boolean devLike = environment.acceptsProfiles(Profiles.of("dev", "local", "test"));

        if ("smtp".equals(provider)) {
            log.info("Email provider: SMTP ({}:{})", properties.getSmtp().getHost(), properties.getSmtp().getPort());
            return new SmtpEmailSender(properties);
        }
        if (!devLike) {
            log.warn("Email provider is 'logging': NO EMAIL WILL BE SENT. Set EMAIL_PROVIDER=smtp with SMTP_* settings.");
        }
        return new LoggingEmailSender();
    }
}
