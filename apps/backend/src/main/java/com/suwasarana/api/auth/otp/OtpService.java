package com.suwasarana.api.auth.otp;

import com.suwasarana.api.auth.AuthCodes;
import com.suwasarana.api.auth.AuthException;
import com.suwasarana.api.auth.AuthMessages;
import com.suwasarana.api.notification.sms.SmsProvider;
import com.suwasarana.api.security.HmacHasher;
import com.suwasarana.api.user.PhoneNumbers;
import com.suwasarana.api.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Phone ownership proof by one-time code. Codes are stored only as HMAC-SHA256 digests, compared in constant time,
 * and phone numbers appear in logs masked. A code is valid for 10 minutes, 5 wrong guesses lock it, a new code
 * can be requested once a minute, and a verified phone stays "verified" for 30 minutes so the form can be finished.
 */
@Service
public class OtpService {

    private static final Logger log = LoggerFactory.getLogger(OtpService.class);

    static final String HASH_PURPOSE = "otp";
    private static final int OTP_VALIDITY_MINUTES = 10;
    /** After a successful check the phone stays "verified" for this long, so the user can finish the sign-up form. */
    private static final int VERIFIED_VALIDITY_MINUTES = 30;
    private static final int MAX_ATTEMPTS = 5;
    private static final int RESEND_COOLDOWN_SECONDS = 60;

    private final OtpVerificationRepository otpRepository;
    private final SmsProvider smsProvider;
    private final HmacHasher hasher;
    private final AuthMessages messages;
    private final UserRepository userRepository;

    private final SecureRandom random = new SecureRandom();

    public OtpService(OtpVerificationRepository otpRepository, SmsProvider smsProvider, HmacHasher hasher,
                      AuthMessages messages, UserRepository userRepository) {
        this.otpRepository = otpRepository;
        this.smsProvider = smsProvider;
        this.hasher = hasher;
        this.messages = messages;
        this.userRepository = userRepository;
    }

    public void generateAndSendOtp(String phoneNumber) {
        generateAndSendOtp(phoneNumber, Locale.ENGLISH);
    }

    /**
     * Sends a fresh code. The SMS language is the account's preferred language when the number belongs to a user,
     * otherwise {@code fallbackLocale} (the request's Accept-Language).
     */
    public void generateAndSendOtp(String phoneNumber, Locale fallbackLocale) {
        String phone = PhoneNumbers.normalize(phoneNumber);
        if (phone == null) {
            throw AuthException.invalidField("phoneNumber", AuthCodes.PHONE_INVALID);
        }
        List<String> variants = PhoneNumbers.variants(phone);

        Optional<OtpVerification> latest = otpRepository.findTopByPhoneNumberInOrderByCreatedAtDesc(variants);
        if (latest.isPresent()) {
            LocalDateTime next = latest.get().getCreatedAt().plusSeconds(RESEND_COOLDOWN_SECONDS);
            if (next.isAfter(LocalDateTime.now())) {
                int wait = (int) Math.max(1, Duration.between(LocalDateTime.now(), next).toSeconds());
                throw AuthException.tooManyRequests(AuthCodes.OTP_COOLDOWN, wait);
            }
        }

        String otp = String.format("%06d", random.nextInt(1_000_000));

        OtpVerification verification = new OtpVerification();
        verification.setPhoneNumber(phone);
        verification.setOtpCode(hasher.hash(HASH_PURPOSE, phone + ":" + otp));
        verification.setExpiresAt(LocalDateTime.now().plusMinutes(OTP_VALIDITY_MINUTES));
        verification = otpRepository.save(verification);

        String language = userRepository.findFirstByPhoneNumberIn(variants)
                .map(u -> u.getPreferredLanguage())
                .orElseGet(() -> AuthMessages.languageOf(fallbackLocale));
        String message = messages.get(language, "sms.otp", otp, OTP_VALIDITY_MINUTES);

        if (!smsProvider.sendSms(phone, message)) {
            otpRepository.delete(verification); // nothing was delivered: do not make the user wait out the cooldown
            log.warn("OTP could not be delivered to {}", PhoneNumbers.mask(phone));
            throw new AuthException(HttpStatus.BAD_GATEWAY, AuthCodes.SMS_FAILED);
        }
        log.info("Sent OTP to {}", PhoneNumbers.mask(phone));
    }

    @Transactional
    public boolean verifyOtp(String phoneNumber, String code) {
        String phone = PhoneNumbers.normalize(phoneNumber);
        if (phone == null) {
            return false;
        }
        Optional<OtpVerification> optVerification = otpRepository.findTopByPhoneNumberInOrderByCreatedAtDesc(PhoneNumbers.variants(phone));
        if (optVerification.isEmpty()) {
            log.warn("No OTP record for {}", PhoneNumbers.mask(phone));
            return false;
        }

        OtpVerification verification = optVerification.get();
        LocalDateTime now = LocalDateTime.now();

        if (verification.getExpiresAt().isBefore(now)) {
            log.warn("OTP expired for {}", PhoneNumbers.mask(phone));
            return false;
        }
        if (verification.getAttempts() >= MAX_ATTEMPTS) {
            log.warn("OTP locked after too many attempts for {}", PhoneNumbers.mask(phone));
            return false;
        }

        boolean matches = code != null && !code.isBlank()
                && HmacHasher.constantTimeEquals(verification.getOtpCode(),
                        hasher.hash(HASH_PURPOSE, verification.getPhoneNumber() + ":" + code.trim()));

        if (!matches) {
            verification.setAttempts(verification.getAttempts() + 1);
            otpRepository.save(verification);
            log.warn("Wrong OTP for {}", PhoneNumbers.mask(phone));
            return false;
        }

        if (!verification.isVerified()) {
            verification.setVerified(true);
            verification.setExpiresAt(now.plusMinutes(VERIFIED_VALIDITY_MINUTES));
            otpRepository.save(verification);
            log.info("Verified OTP for {}", PhoneNumbers.mask(phone));
        }
        return true;
    }

    public boolean isPhoneVerified(String phoneNumber) {
        return otpRepository.findTopByPhoneNumberInOrderByCreatedAtDesc(PhoneNumbers.variants(phoneNumber))
                .map(v -> v.isVerified() && v.getExpiresAt().isAfter(LocalDateTime.now()))
                .orElse(false);
    }

    /** Called once the account exists so the same verification cannot be reused. */
    @Transactional
    public void consume(String phoneNumber) {
        otpRepository.deleteByPhoneNumberIn(PhoneNumbers.variants(phoneNumber));
    }
}
