package com.suwasarana.api.auth.otp;

import com.suwasarana.api.auth.AuthCodes;
import com.suwasarana.api.auth.AuthException;
import com.suwasarana.api.auth.AuthMessages;
import com.suwasarana.api.auth.AuthProperties;
import com.suwasarana.api.notification.sms.SmsProvider;
import com.suwasarana.api.security.HmacHasher;
import com.suwasarana.api.user.User;
import com.suwasarana.api.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OtpServiceTest {

    private static final String PHONE = "+94771234567";

    @Mock private OtpVerificationRepository repository;
    @Mock private SmsProvider smsProvider;
    @Mock private UserRepository userRepository;

    private HmacHasher hasher;
    private OtpService otpService;

    @BeforeEach
    void setUp() {
        hasher = new HmacHasher(new AuthProperties());
        otpService = new OtpService(repository, smsProvider, hasher, new AuthMessages(), userRepository);
        when(smsProvider.sendSms(anyString(), anyString())).thenReturn(true);
        when(userRepository.findFirstByPhoneNumberIn(anyCollection())).thenReturn(Optional.empty());
    }

    private String hashOf(String code) {
        return hasher.hash(OtpService.HASH_PURPOSE, PHONE + ":" + code);
    }

    private OtpVerification record(String code, boolean verified, LocalDateTime expiresAt, int attempts) {
        OtpVerification v = new OtpVerification();
        v.setPhoneNumber(PHONE);
        v.setOtpCode(hashOf(code));
        v.setVerified(verified);
        v.setExpiresAt(expiresAt);
        v.setAttempts(attempts);
        return v;
    }

    private void latestIs(OtpVerification v) {
        when(repository.findTopByPhoneNumberInOrderByCreatedAtDesc(anyCollection())).thenReturn(Optional.ofNullable(v));
    }

    @Test
    void correctCode_marksPhoneVerified() {
        OtpVerification v = record("123456", false, LocalDateTime.now().plusMinutes(5), 0);
        latestIs(v);

        assertTrue(otpService.verifyOtp("0771234567", "123456")); // any spelling of the number works
        assertTrue(v.isVerified());
    }

    @Test
    void wrongCode_countsAttempt_andLocksAfterFive() {
        OtpVerification v = record("123456", false, LocalDateTime.now().plusMinutes(5), 0);
        latestIs(v);

        for (int i = 0; i < 5; i++) {
            assertFalse(otpService.verifyOtp(PHONE, "000000"));
        }
        assertEquals(5, v.getAttempts());

        assertFalse(otpService.verifyOtp(PHONE, "123456"), "even the right code is refused once locked");
        assertFalse(v.isVerified());
    }

    @Test
    void expiredCode_isRejected() {
        latestIs(record("123456", false, LocalDateTime.now().minusMinutes(1), 0));

        assertFalse(otpService.verifyOtp(PHONE, "123456"));
    }

    @Test
    void verifiedPhone_stopsBeingAcceptedAfterItsWindow() {
        latestIs(record("123456", true, LocalDateTime.now().plusMinutes(10), 0));
        assertTrue(otpService.isPhoneVerified(PHONE));

        latestIs(record("123456", true, LocalDateTime.now().minusMinutes(1), 0));
        assertFalse(otpService.isPhoneVerified(PHONE));
    }

    @Test
    void sendOtp_storesOnlyAnHmacOfTheCode_andTheSmsCarriesTheCode() {
        latestIs(null);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        otpService.generateAndSendOtp("077 123 4567", Locale.ENGLISH);

        ArgumentCaptor<OtpVerification> saved = ArgumentCaptor.forClass(OtpVerification.class);
        verify(repository).save(saved.capture());
        ArgumentCaptor<String> sms = ArgumentCaptor.forClass(String.class);
        verify(smsProvider).sendSms(eq(PHONE), sms.capture());

        Matcher m = Pattern.compile("\\b(\\d{6})\\b").matcher(sms.getValue());
        assertTrue(m.find(), "the SMS text contains the six-digit code");
        String code = m.group(1);

        assertEquals(PHONE, saved.getValue().getPhoneNumber(), "stored in E.164");
        assertEquals(64, saved.getValue().getOtpCode().length());
        assertFalse(saved.getValue().getOtpCode().contains(code), "no plaintext code at rest");
        assertEquals(hashOf(code), saved.getValue().getOtpCode());
    }

    @Test
    void sendOtp_usesTheAccountsPreferredLanguage() {
        latestIs(null);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        User sinhala = new User();
        sinhala.setPreferredLanguage("si");
        when(userRepository.findFirstByPhoneNumberIn(anyCollection())).thenReturn(Optional.of(sinhala));

        otpService.generateAndSendOtp(PHONE, Locale.ENGLISH);

        ArgumentCaptor<String> sms = ArgumentCaptor.forClass(String.class);
        verify(smsProvider).sendSms(eq(PHONE), sms.capture());
        assertTrue(sms.getValue().contains("සුව සරණ"), sms.getValue());
    }

    @Test
    void sendOtp_twiceWithinCooldown_is429_withRetryAfter() {
        latestIs(record("123456", false, LocalDateTime.now().plusMinutes(10), 0)); // created "now"

        AuthException ex = assertThrows(AuthException.class, () -> otpService.generateAndSendOtp(PHONE, Locale.ENGLISH));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getStatus());
        assertEquals(AuthCodes.OTP_COOLDOWN, ex.getCode());
        assertNotNull(ex.getRetryAfterSeconds());
        verify(smsProvider, never()).sendSms(any(), any());
    }

    @Test
    void sendOtp_whenTheProviderFails_isReportedAndTheRecordRemoved() {
        latestIs(null);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(smsProvider.sendSms(anyString(), anyString())).thenReturn(false);

        AuthException ex = assertThrows(AuthException.class, () -> otpService.generateAndSendOtp(PHONE, Locale.ENGLISH));

        assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatus());
        assertEquals(AuthCodes.SMS_FAILED, ex.getCode());
        verify(repository).delete(any(OtpVerification.class));
    }

    @Test
    void sendOtp_toAnInvalidNumber_is400() {
        AuthException ex = assertThrows(AuthException.class, () -> otpService.generateAndSendOtp("12345", Locale.ENGLISH));

        assertEquals(AuthCodes.PHONE_INVALID, ex.getFieldErrors().get("phoneNumber"));
        verifyNoInteractions(smsProvider);
    }

    @Test
    void constantTimeComparison_isUsedForTheHash() {
        assertTrue(HmacHasher.constantTimeEquals("abc", "abc"));
        assertFalse(HmacHasher.constantTimeEquals("abc", "abd"));
        assertFalse(HmacHasher.constantTimeEquals(null, "abc"));
    }
}
