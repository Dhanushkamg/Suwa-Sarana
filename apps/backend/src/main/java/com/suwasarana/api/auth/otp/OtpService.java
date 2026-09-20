package com.suwasarana.api.auth.otp;

import com.suwasarana.api.notification.sms.SmsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Random;

@Service
public class OtpService {

    private static final Logger log = LoggerFactory.getLogger(OtpService.class);
    private static final int OTP_VALIDITY_MINUTES = 10;

    @Autowired
    private OtpVerificationRepository otpRepository;

    @Autowired
    private SmsProvider smsProvider;

    private final Random random = new Random();

    public void generateAndSendOtp(String phoneNumber) {
        // Generate a 6-digit OTP
        String otp = String.format("%06d", random.nextInt(999999));

        // Save it to DB
        OtpVerification verification = new OtpVerification();
        verification.setPhoneNumber(phoneNumber);
        verification.setOtpCode(otp);
        verification.setExpiresAt(LocalDateTime.now().plusMinutes(OTP_VALIDITY_MINUTES));
        otpRepository.save(verification);

        // Send via SMS
        String message = "Your Suwa Sarana verification code is: " + otp + ". It is valid for " + OTP_VALIDITY_MINUTES + " minutes.";
        smsProvider.sendSms(phoneNumber, message);
        log.info("Sent OTP to {}", phoneNumber);
    }

    public boolean verifyOtp(String phoneNumber, String code) {
        Optional<OtpVerification> optVerification = otpRepository.findTopByPhoneNumberOrderByCreatedAtDesc(phoneNumber);

        if (optVerification.isPresent()) {
            OtpVerification verification = optVerification.get();

            // Check if already verified
            if (verification.isVerified()) {
                return true;
            }

            // Check if expired
            if (verification.getExpiresAt().isBefore(LocalDateTime.now())) {
                log.warn("OTP expired for phone number: {}", phoneNumber);
                return false;
            }

            // Check if code matches
            if (verification.getOtpCode().equals(code)) {
                verification.setVerified(true);
                otpRepository.save(verification);
                log.info("Successfully verified OTP for phone number: {}", phoneNumber);
                return true;
            } else {
                log.warn("Invalid OTP code provided for phone number: {}", phoneNumber);
            }
        } else {
            log.warn("No OTP record found for phone number: {}", phoneNumber);
        }

        return false;
    }

    public boolean isPhoneVerified(String phoneNumber) {
        Optional<OtpVerification> optVerification = otpRepository.findTopByPhoneNumberOrderByCreatedAtDesc(phoneNumber);
        return optVerification.isPresent() && optVerification.get().isVerified();
    }
}
