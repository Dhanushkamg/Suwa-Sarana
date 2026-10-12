package com.suwasarana.api.auth.otp;

import com.suwasarana.api.common.ApiException;
import com.suwasarana.api.notification.sms.SmsProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OtpServiceTest {

    private static final String PHONE = "0771234567";

    @Mock
    private OtpVerificationRepository repository;

    @Mock
    private SmsProvider smsProvider;

    @InjectMocks
    private OtpService otpService;

    private OtpVerification record(String code, boolean verified, LocalDateTime expiresAt, int attempts) {
        OtpVerification v = new OtpVerification();
        v.setPhoneNumber(PHONE);
        v.setOtpCode(code);
        v.setVerified(verified);
        v.setExpiresAt(expiresAt);
        v.setAttempts(attempts);
        return v;
    }

    @Test
    void correctCode_marksPhoneVerified() {
        OtpVerification v = record("123456", false, LocalDateTime.now().plusMinutes(5), 0);
        when(repository.findTopByPhoneNumberOrderByCreatedAtDesc(PHONE)).thenReturn(Optional.of(v));

        assertTrue(otpService.verifyOtp(PHONE, "123456"));
        assertTrue(v.isVerified());
    }

    @Test
    void wrongCode_countsAttempt_andLocksAfterFive() {
        OtpVerification v = record("123456", false, LocalDateTime.now().plusMinutes(5), 0);
        when(repository.findTopByPhoneNumberOrderByCreatedAtDesc(PHONE)).thenReturn(Optional.of(v));

        for (int i = 0; i < 5; i++) {
            assertFalse(otpService.verifyOtp(PHONE, "000000"));
        }
        assertEquals(5, v.getAttempts());

        // Even the right code is refused once locked
        assertFalse(otpService.verifyOtp(PHONE, "123456"));
        assertFalse(v.isVerified());
    }

    @Test
    void expiredCode_isRejected() {
        OtpVerification v = record("123456", false, LocalDateTime.now().minusMinutes(1), 0);
        when(repository.findTopByPhoneNumberOrderByCreatedAtDesc(PHONE)).thenReturn(Optional.of(v));

        assertFalse(otpService.verifyOtp(PHONE, "123456"));
    }

    @Test
    void verifiedPhone_stopsBeingAcceptedAfterItsWindow() {
        OtpVerification fresh = record("123456", true, LocalDateTime.now().plusMinutes(10), 0);
        when(repository.findTopByPhoneNumberOrderByCreatedAtDesc(PHONE)).thenReturn(Optional.of(fresh));
        assertTrue(otpService.isPhoneVerified(PHONE));

        OtpVerification stale = record("123456", true, LocalDateTime.now().minusMinutes(1), 0);
        when(repository.findTopByPhoneNumberOrderByCreatedAtDesc(PHONE)).thenReturn(Optional.of(stale));
        assertFalse(otpService.isPhoneVerified(PHONE));
    }

    @Test
    void sendOtp_generatesSixDigitCode_andSendsSms() {
        when(repository.findTopByPhoneNumberOrderByCreatedAtDesc(PHONE)).thenReturn(Optional.empty());

        otpService.generateAndSendOtp(PHONE);

        ArgumentCaptor<OtpVerification> saved = ArgumentCaptor.forClass(OtpVerification.class);
        verify(repository).save(saved.capture());
        assertTrue(saved.getValue().getOtpCode().matches("\\d{6}"));
        verify(smsProvider).sendSms(anyString(), anyString());
    }

    @Test
    void sendOtp_twiceWithinCooldown_isRejected() {
        OtpVerification recent = record("123456", false, LocalDateTime.now().plusMinutes(10), 0);
        when(repository.findTopByPhoneNumberOrderByCreatedAtDesc(PHONE)).thenReturn(Optional.of(recent));

        assertThrows(ApiException.class, () -> otpService.generateAndSendOtp(PHONE));
        verify(smsProvider, never()).sendSms(any(), any());
    }
}
