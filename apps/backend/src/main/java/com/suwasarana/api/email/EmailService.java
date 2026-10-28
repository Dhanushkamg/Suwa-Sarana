package com.suwasarana.api.email;

import com.suwasarana.api.auth.AuthMessages;
import com.suwasarana.api.auth.AuthProperties;
import com.suwasarana.api.user.User;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Builds the localised transactional emails (text from {@code i18n/auth_<lang>.properties}) and hands them to the
 * {@link EmailSender} on a small background pool, so a slow mail server never slows (or, by timing, reveals anything
 * about) an HTTP request. The recipient's {@code preferred_language} picks the language.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final EmailSender sender;
    private final AuthMessages messages;
    private final AuthProperties properties;
    private final Executor executor;
    private final ExecutorService ownedPool;

    @Autowired
    public EmailService(EmailSender sender, AuthMessages messages, AuthProperties properties) {
        this.sender = sender;
        this.messages = messages;
        this.properties = properties;
        this.ownedPool = new ThreadPoolExecutor(1, 2, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(500),
                r -> {
                    Thread t = new Thread(r, "email-sender");
                    t.setDaemon(true);
                    return t;
                });
        this.executor = ownedPool;
    }

    /** For tests: run sends on the calling thread. */
    public EmailService(EmailSender sender, AuthMessages messages, AuthProperties properties, Executor executor) {
        this.sender = sender;
        this.messages = messages;
        this.properties = properties;
        this.executor = executor;
        this.ownedPool = null;
    }

    @PreDestroy
    void shutdown() {
        if (ownedPool != null) {
            ownedPool.shutdown();
        }
    }

    // ---- the mails -------------------------------------------------------------------------------------------

    public void sendEmailVerification(User user, String rawToken) {
        String link = properties.frontendBase() + "/verify-email?token=" + enc(rawToken);
        send(user.getEmail(), user.getPreferredLanguage(), "email.verify",
                greeting(user), link, properties.getEmailVerificationTtlHours());
    }

    public void sendPasswordReset(User user, String rawToken) {
        String link = properties.frontendBase() + "/reset-password?token=" + enc(rawToken);
        send(user.getEmail(), user.getPreferredLanguage(), "email.reset",
                greeting(user), link, properties.getPasswordResetTtlMinutes());
    }

    /** @param language invitee's language if known (en otherwise) */
    public void sendAdminInvite(String email, String firstName, String language, String rawToken) {
        send(email, language, "email.invite",
                firstName == null || firstName.isBlank() ? email : firstName, adminInviteLink(email, rawToken),
                properties.getAdminInviteTtlHours());
    }

    /** The page where an invited administrator creates the account. */
    public String adminInviteLink(String email, String rawToken) {
        return properties.frontendBase() + "/accept-invite?token=" + enc(rawToken) + "&email=" + enc(email);
    }

    public void sendVerificationDecision(User user, boolean approved, String reason) {
        send(user.getEmail(), user.getPreferredLanguage(), approved ? "email.approved" : "email.rejected",
                greeting(user), reason == null || reason.isBlank() ? "-" : reason);
    }

    public void sendSuspended(User user, String reason) {
        send(user.getEmail(), user.getPreferredLanguage(), "email.suspended", greeting(user), reason == null ? "-" : reason);
    }

    public void sendRestored(User user) {
        send(user.getEmail(), user.getPreferredLanguage(), "email.restored", greeting(user));
    }

    /** Tells the owner of an address that someone tried to register it again (instead of telling the registrant). */
    public void sendDuplicateRegistrationNotice(User existing) {
        send(existing.getEmail(), existing.getPreferredLanguage(), "email.duplicate", greeting(existing),
                properties.frontendBase() + "/login", properties.frontendBase() + "/forgot-password");
    }

    public void sendAccountDeleted(String email, String firstName, String language) {
        send(email, language, "email.deleted", firstName == null || firstName.isBlank() ? email : firstName);
    }

    // ---- plumbing --------------------------------------------------------------------------------------------

    private void send(String to, String language, String keyPrefix, Object... args) {
        String subject = messages.get(language, keyPrefix + ".subject");
        String body = messages.get(language, keyPrefix + ".body", args);
        EmailMessage message = new EmailMessage(to, subject, body);
        try {
            executor.execute(() -> {
                try {
                    if (!sender.send(message)) {
                        log.warn("Email '{}' to {} was not delivered", keyPrefix, LoggingEmailSender.maskEmail(to));
                    }
                } catch (RuntimeException e) {
                    log.warn("Email '{}' to {} failed: {}", keyPrefix, LoggingEmailSender.maskEmail(to), e.getClass().getSimpleName());
                }
            });
        } catch (RejectedExecutionException e) {
            log.warn("Email queue full: '{}' to {} dropped", keyPrefix, LoggingEmailSender.maskEmail(to));
        }
    }

    private static String greeting(User user) {
        String name = user.getFirstName();
        return name == null || name.isBlank() ? user.getEmail() : name;
    }

    private static String enc(String value) {
        return UriUtils.encodeQueryParam(value, StandardCharsets.UTF_8);
    }
}
