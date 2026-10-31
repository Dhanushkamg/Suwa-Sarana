package com.suwasarana.api.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Development sender: nothing leaves the machine. Only the masked recipient and the subject are logged; the body
 * (verification / reset links, invitations) never is.
 */
public class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    @Override
    public boolean send(EmailMessage message) {
        if (message == null || message.to() == null) {
            return false;
        }
        log.info("[EMAIL not sent - logging sender] to={} subject={}", maskEmail(message.to()), message.subject());
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
