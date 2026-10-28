package com.suwasarana.api.auth;

import com.suwasarana.api.audit.AuditLogService;
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

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LoginAttemptServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private AuditLogService auditLog;

    private AuthProperties properties;
    private LoginAttemptService service;
    private User user;

    @BeforeEach
    void setUp() {
        properties = new AuthProperties(); // threshold 5, base 60 s, max 3600 s
        service = new LoginAttemptService(userRepository, properties, auditLog);
        user = new User();
        user.setId(7L);
    }

    private void afterIncrementTheCountIs(int failures) {
        user.setFailedLoginCount(failures);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
    }

    @Test
    void lockDurations_doubleEveryThresholdAndAreCapped() {
        AuthProperties.Lockout cfg = properties.getLockout();
        assertEquals(Duration.ZERO, LoginAttemptService.lockDuration(1, cfg));
        assertEquals(Duration.ZERO, LoginAttemptService.lockDuration(4, cfg));
        assertEquals(Duration.ofSeconds(60), LoginAttemptService.lockDuration(5, cfg));
        assertEquals(Duration.ZERO, LoginAttemptService.lockDuration(6, cfg));
        assertEquals(Duration.ofSeconds(120), LoginAttemptService.lockDuration(10, cfg));
        assertEquals(Duration.ofSeconds(240), LoginAttemptService.lockDuration(15, cfg));
        assertEquals(Duration.ofSeconds(480), LoginAttemptService.lockDuration(20, cfg));
        assertEquals(Duration.ofSeconds(3600), LoginAttemptService.lockDuration(500, cfg));
    }

    @Test
    void fourthFailure_doesNotLock() {
        afterIncrementTheCountIs(4);

        service.recordFailure(user);

        verify(userRepository).incrementFailedLogins(7L);
        verify(userRepository, never()).setLockedUntil(any(), any());
    }

    @Test
    void fifthFailure_locksForAboutAMinute() {
        afterIncrementTheCountIs(5);

        service.recordFailure(user);

        ArgumentCaptor<Instant> until = ArgumentCaptor.forClass(Instant.class);
        verify(userRepository).setLockedUntil(eq(7L), until.capture());
        long seconds = Duration.between(Instant.now(), until.getValue()).toSeconds();
        assertTrue(seconds >= 58 && seconds <= 60, "locked for ~60 s but was " + seconds);
    }

    @Test
    void whileLocked_loginIsRefusedWithRetryAfter_andNothingIsCounted() {
        user.setLockedUntil(Instant.now().plusSeconds(90));

        AuthException ex = assertThrows(AuthException.class, () -> service.assertNotLocked(user, "10.0.0.9"));

        assertEquals(AuthCodes.ACCOUNT_LOCKED, ex.getCode());
        assertEquals(429, ex.getStatus().value());
        assertTrue(ex.getRetryAfterSeconds() >= 89 && ex.getRetryAfterSeconds() <= 91);
        verify(auditLog).logAccountLocked(7L, "10.0.0.9");
        verify(userRepository, never()).incrementFailedLogins(any());
    }

    @Test
    void afterTheLockExpires_loginIsAllowedAgain() {
        user.setLockedUntil(Instant.now().minusSeconds(1));

        assertDoesNotThrow(() -> service.assertNotLocked(user, "ip"));
    }

    @Test
    void success_clearsCounterAndLock_butOnlyWhenThereIsSomethingToClear() {
        service.recordSuccess(user);
        verify(userRepository, never()).clearLoginFailures(any());

        user.setFailedLoginCount(3);
        service.recordSuccess(user);
        verify(userRepository).clearLoginFailures(7L);
    }
}
