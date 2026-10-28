package com.suwasarana.api.email;

import com.suwasarana.api.auth.AuthMessages;
import com.suwasarana.api.notification.sms.SmsProvider;
import com.suwasarana.api.user.PhoneNumbers;
import com.suwasarana.api.user.User;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Tells a user about a decision on their account by email AND SMS, in their preferred language. Best effort and
 * asynchronous: a failing provider never fails the administrator's action (the decision is already persisted).
 * SMS texts never contain the free-text reason (it may be long and is visible in the email / the app).
 */
@Service
public class AccountNotifier {

    private static final Logger log = LoggerFactory.getLogger(AccountNotifier.class);

    private final EmailService emailService;
    private final SmsProvider smsProvider;
    private final AuthMessages messages;
    private final Executor smsExecutor;
    private final ExecutorService ownedPool;

    @Autowired
    public AccountNotifier(EmailService emailService, SmsProvider smsProvider, AuthMessages messages) {
        this.emailService = emailService;
        this.smsProvider = smsProvider;
        this.messages = messages;
        this.ownedPool = new ThreadPoolExecutor(1, 2, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(500), r -> {
            Thread t = new Thread(r, "sms-sender");
            t.setDaemon(true);
            return t;
        });
        this.smsExecutor = ownedPool;
    }

    /** For tests: run SMS sends on the calling thread. */
    public AccountNotifier(EmailService emailService, SmsProvider smsProvider, AuthMessages messages, Executor smsExecutor) {
        this.emailService = emailService;
        this.smsProvider = smsProvider;
        this.messages = messages;
        this.smsExecutor = smsExecutor;
        this.ownedPool = null;
    }

    @PreDestroy
    void shutdown() {
        if (ownedPool != null) {
            ownedPool.shutdown();
        }
    }

    public void verificationDecision(User user, boolean approved, String reason) {
        emailService.sendVerificationDecision(user, approved, reason);
        sms(user, approved ? "sms.verification.approved" : "sms.verification.rejected");
    }

    public void suspended(User user, String reason) {
        emailService.sendSuspended(user, reason);
        sms(user, "sms.account.suspended");
    }

    public void restored(User user) {
        emailService.sendRestored(user);
        sms(user, "sms.account.restored");
    }

    private void sms(User user, String key) {
        String phone = user.getPhoneNumber();
        String text = messages.get(user.getPreferredLanguage(), key);
        try {
            smsExecutor.execute(() -> {
                try {
                    if (!smsProvider.sendSms(phone, text)) {
                        log.warn("Account SMS '{}' to {} was not delivered", key, PhoneNumbers.mask(phone));
                    }
                } catch (RuntimeException e) {
                    log.warn("Account SMS '{}' to {} failed: {}", key, PhoneNumbers.mask(phone), e.getClass().getSimpleName());
                }
            });
        } catch (RejectedExecutionException e) {
            log.warn("SMS queue full: '{}' to {} dropped", key, PhoneNumbers.mask(phone));
        }
    }
}
