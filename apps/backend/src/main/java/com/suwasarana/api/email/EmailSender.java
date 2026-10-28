package com.suwasarana.api.email;

/**
 * Delivers one email. Implementations: {@link SmtpEmailSender} (real delivery over SMTP, e.g. Amazon SES) and
 * {@link LoggingEmailSender} (development: writes to the log, sends nothing).
 */
public interface EmailSender {

    /** @return true when the mail server accepted the message; false on any failure (never throws). */
    boolean send(EmailMessage message);
}
