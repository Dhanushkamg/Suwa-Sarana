package com.suwasarana.api.auth;

import com.suwasarana.api.email.EmailProperties;
import com.suwasarana.api.notification.sms.SmsProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/** Registers the settings of the auth module and refuses to run production with the built-in development HMAC secret. */
@Configuration
@PropertySource("classpath:suwa-auth-defaults.properties")
@EnableConfigurationProperties({AuthProperties.class, SmsProperties.class, EmailProperties.class})
public class AuthConfig {

    private static final Logger log = LoggerFactory.getLogger(AuthConfig.class);

    @Autowired
    private AuthProperties properties;

    @Autowired
    private Environment environment;

    @PostConstruct
    void checkSecrets() {
        String secret = properties.getHmacSecret();
        boolean devDefault = AuthProperties.DEV_HMAC_SECRET.equals(secret);
        boolean weak = secret == null || secret.length() < 32;
        if (environment.acceptsProfiles(Profiles.of("prod", "production"))) {
            if (devDefault || weak) {
                throw new IllegalStateException("AUTH_HMAC_SECRET must be set to a random value of at least 32 characters in production.");
            }
        } else if (devDefault) {
            log.warn("Using the built-in development HMAC secret (AUTH_HMAC_SECRET). Never do this in production.");
        }
    }
}
