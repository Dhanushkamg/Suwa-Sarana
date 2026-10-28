package com.suwasarana.api.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Development sender: nothing leaves the machine. With {@code logBody} (dev/local/test profiles) the whole mail,
 * including its links, is printed so a developer can follow them; otherwise only the masked recipient and the subject.
 */
public class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    private final boolean logBody;

    public LoggingEmailSender(boolean logBody) {
        this.logBody = logBody;
    }

    @Override
    public boolean send(EmailMessage message) {
        if (message == null || message.to() == null) {
            return false;
        }
        if (logBody) {
            log.info("[EMAIL not sent - logging sender]\nTo: {}\nSubject: {}\n\n{}", message.to(), message.subject(), message.body());
        } else {
            log.info("[EMAIL not sent - logging sender] to={} subject={}", maskEmail(message.to()), message.subject());
        }
        return true;
    }

    /** "someone@example.com" -> "s*****@example.com" */
    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "<none>";
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "*****";
        }
        return email.charAt(0) + "*****" + email.substring(at);
    }
}
