package com.suwasarana.api.auth.otp;

import com.suwasarana.api.common.ApiException;
import com.suwasarana.api.notification.sms.SmsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class OtpService {

    private static final Logger log = LoggerFactory.getLogger(OtpService.class);

    private static final int OTP_VALIDITY_MINUTES = 10;
    /** After a successful check the phone stays "verified" for this long, so the user can finish the sign-up form. */
    private static final int VERIFIED_VALIDITY_MINUTES = 30;
    private static final int MAX_ATTEMPTS = 5;
    private static final int RESEND_COOLDOWN_SECONDS = 60;

    @Autowired
    private OtpVerificationRepository otpRepository;

    @Autowired
    private SmsProvider smsProvider;

    private final SecureRandom random = new SecureRandom();

    @Transactional
    public void generateAndSendOtp(String phoneNumber) {
        Optional<OtpVerification> latest = otpRepository.findTopByPhoneNumberOrderByCreatedAtDesc(phoneNumber);
        if (latest.isPresent() && latest.get().getCreatedAt().plusSeconds(RESEND_COOLDOWN_SECONDS).isAfter(LocalDateTime.now())) {
            throw ApiException.tooManyRequests("Please wait a minute before requesting another code.");
        }

        String otp = String.format("%06d", random.nextInt(1_000_000));

        OtpVerification verification = new OtpVerification();
        verification.setPhoneNumber(phoneNumber);
        verification.setOtpCode(otp);
        verification.setExpiresAt(LocalDateTime.now().plusMinutes(OTP_VALIDITY_MINUTES));
        otpRepository.save(verification);

        String message = "Your Suwa Sarana verification code is: " + otp + ". It is valid for " + OTP_VALIDITY_MINUTES + " minutes.";
        smsProvider.sendSms(phoneNumber, message);
        log.info("Sent OTP to {}", phoneNumber);
    }

    @Transactional
    public boolean verifyOtp(String phoneNumber, String code) {
        Optional<OtpVerification> optVerification = otpRepository.findTopByPhoneNumberOrderByCreatedAtDesc(phoneNumber);
        if (optVerification.isEmpty()) {
            log.warn("No OTP record found for phone number: {}", phoneNumber);
            return false;
        }

        OtpVerification verification = optVerification.get();
        LocalDateTime now = LocalDateTime.now();

        if (verification.getExpiresAt().isBefore(now)) {
            log.warn("OTP expired for phone number: {}", phoneNumber);
            return false;
        }
        if (verification.getAttempts() >= MAX_ATTEMPTS) {
            log.warn("OTP locked after too many attempts for phone number: {}", phoneNumber);
            return false;
        }

        boolean matches = code != null && MessageDigest.isEqual(
                verification.getOtpCode().getBytes(StandardCharsets.UTF_8),
                code.getBytes(StandardCharsets.UTF_8));

        if (!matches) {
            verification.setAttempts(verification.getAttempts() + 1);
            otpRepository.save(verification);
            log.warn("Invalid OTP code provided for phone number: {}", phoneNumber);
            return false;
        }

        if (!verification.isVerified()) {
            verification.setVerified(true);
            verification.setExpiresAt(now.plusMinutes(VERIFIED_VALIDITY_MINUTES));
            otpRepository.save(verification);
            log.info("Successfully verified OTP for phone number: {}", phoneNumber);
        }
        return true;
    }

    public boolean isPhoneVerified(String phoneNumber) {
        return otpRepository.findTopByPhoneNumberOrderByCreatedAtDesc(phoneNumber)
                .map(v -> v.isVerified() && v.getExpiresAt().isAfter(LocalDateTime.now()))
                .orElse(false);
    }

    /** Called once the account exists so the same verification cannot be reused. */
    @Transactional
    public void consume(String phoneNumber) {
        otpRepository.deleteByPhoneNumber(phoneNumber);
    }
}
