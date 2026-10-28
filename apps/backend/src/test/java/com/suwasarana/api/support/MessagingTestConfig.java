package com.suwasarana.api.support;

import com.suwasarana.api.auth.AuthCodes;
import com.suwasarana.api.auth.AuthException;
import com.suwasarana.api.auth.AuthMessages;
import com.suwasarana.api.auth.GoogleIdentityVerifier;
import com.suwasarana.api.auth.AuthProperties;
import com.suwasarana.api.email.AccountNotifier;
import com.suwasarana.api.email.EmailMessage;
import com.suwasarana.api.email.EmailSender;
import com.suwasarana.api.email.EmailService;
import com.suwasarana.api.notification.sms.SmsProvider;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Swaps the outbound channels for in-memory recorders and makes every send synchronous, so integration tests can read
 * the OTP codes, verification links and decision mails exactly as a real user would receive them.
 */
@TestConfiguration
public class MessagingTestConfig {

    public record Sms(String to, String text) {
    }

    public static class Outbox {
        public final List<Sms> sms = new CopyOnWriteArrayList<>();
        public final List<EmailMessage> emails = new CopyOnWriteArrayList<>();
        public volatile boolean smsWorks = true;

        public void clear() {
            sms.clear();
            emails.clear();
            smsWorks = true;
        }

        public Sms lastSmsTo(String phone) {
            for (int i = sms.size() - 1; i >= 0; i--) {
                if (sms.get(i).to().equals(phone)) {
                    return sms.get(i);
                }
            }
            return null;
        }

        public EmailMessage lastEmailTo(String email) {
            for (int i = emails.size() - 1; i >= 0; i--) {
                if (emails.get(i).to().equalsIgnoreCase(email)) {
                    return emails.get(i);
                }
            }
            return null;
        }

        public long emailsTo(String email) {
            return emails.stream().filter(e -> e.to().equalsIgnoreCase(email)).count();
        }
    }

    @Bean
    public Outbox outbox() {
        return new Outbox();
    }

    @Bean
    @Primary
    public SmsProvider recordingSmsProvider(Outbox outbox) {
        return (to, text) -> {
            if (!outbox.smsWorks) {
                return false;
            }
            outbox.sms.add(new Sms(to, text));
            return true;
        };
    }

    @Bean
    @Primary
    public EmailSender recordingEmailSender(Outbox outbox) {
        return message -> outbox.emails.add(message);
    }

    /** Google ID tokens in tests look like "google:&lt;subject&gt;:&lt;email&gt;"; anything else is an invalid token. */
    @Bean
    @Primary
    public GoogleIdentityVerifier stubGoogleVerifier() {
        return idToken -> {
            if (idToken == null || !idToken.startsWith("google:") || idToken.split(":").length != 3) {
                throw AuthException.unauthorized(AuthCodes.GOOGLE_INVALID);
            }
            String[] parts = idToken.split(":");
            return new GoogleIdentityVerifier.GoogleIdentity(parts[2], parts[1]);
        };
    }

    @Bean
    @Primary
    public EmailService synchronousEmailService(EmailSender sender, AuthMessages messages, AuthProperties properties) {
        return new EmailService(sender, messages, properties, Runnable::run);
    }

    @Bean
    @Primary
    public AccountNotifier synchronousAccountNotifier(EmailService emailService, SmsProvider sms, AuthMessages messages) {
        return new AccountNotifier(emailService, sms, messages, Runnable::run);
    }
}
