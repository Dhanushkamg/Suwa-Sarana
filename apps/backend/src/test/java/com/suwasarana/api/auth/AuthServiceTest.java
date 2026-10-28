package com.suwasarana.api.auth;

import com.suwasarana.api.audit.AdminAuditService;
import com.suwasarana.api.audit.AuditLogService;
import com.suwasarana.api.auth.dto.AuthResponse;
import com.suwasarana.api.auth.dto.LoginDto;
import com.suwasarana.api.auth.dto.ResetPasswordDto;
import com.suwasarana.api.auth.dto.ResetPasswordTokenDto;
import com.suwasarana.api.auth.otp.OtpService;
import com.suwasarana.api.auth.token.AuthToken;
import com.suwasarana.api.auth.token.AuthTokenService;
import com.suwasarana.api.auth.token.AuthTokenType;
import com.suwasarana.api.email.EmailService;
import com.suwasarana.api.security.CustomUserDetailsService;
import com.suwasarana.api.user.Role;
import com.suwasarana.api.user.User;
import com.suwasarana.api.user.UserRepository;
import com.suwasarana.api.user.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private CustomUserDetailsService userLookup;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private RegistrationService registrationService;
    @Mock private SessionService sessionService;
    @Mock private OtpService otpService;
    @Mock private AuthTokenService tokenService;
    @Mock private EmailService emailService;
    @Mock private AuditLogService auditLog;
    @Mock private AdminAuditService adminAudit;

    private AuthProperties properties;
    private LoginAttemptService loginAttempts;
    private AuthService authService;
    private User user;

    @BeforeEach
    void setUp() {
        properties = new AuthProperties();
        loginAttempts = new LoginAttemptService(userRepository, properties, auditLog);
        authService = new AuthService(userRepository, userLookup, passwordEncoder, new PasswordPolicy(), registrationService,
                sessionService, loginAttempts, otpService, tokenService, emailService, auditLog, adminAudit);

        user = new User();
        user.setId(1L);
        user.setEmail("donor@example.com");
        user.setPhoneNumber("+94712345678");
        user.setFirstName("Nimal");
        user.setLastName("Perera");
        user.setRole(Role.DONOR);
        user.setPasswordHash("$2a$12$hash");
        when(userLookup.findUser("donor@example.com")).thenReturn(Optional.of(user));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode(anyString())).thenReturn("encoded");
        when(sessionService.issue(any())).thenAnswer(inv -> AuthResponse.of(inv.getArgument(0), "access", "refresh"));
    }

    private LoginDto login(String username, String password) {
        LoginDto dto = new LoginDto();
        dto.setUsername(username);
        dto.setPassword(password);
        return dto;
    }

    private AuthException thrown(org.junit.jupiter.api.function.Executable e) {
        return assertThrows(AuthException.class, e);
    }

    // ---- login -------------------------------------------------------------------------------------------

    @Test
    void login_validCredentials_returnsSession_andClearsFailures() {
        user.setFailedLoginCount(2);
        when(passwordEncoder.matches("right-pass-123", "$2a$12$hash")).thenReturn(true);

        AuthResponse response = authService.login(login("donor@example.com", "right-pass-123"), "10.0.0.1");

        assertEquals("access", response.getAccessToken());
        verify(userRepository).clearLoginFailures(1L);
    }

    @Test
    void login_wrongPassword_is401_andCountsTheFailure() {
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);
        when(userRepository.findById(1L)).thenAnswer(inv -> { user.setFailedLoginCount(1); return Optional.of(user); });

        AuthException ex = thrown(() -> authService.login(login("donor@example.com", "nope"), "10.0.0.1"));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
        assertEquals(AuthCodes.CREDENTIALS_INVALID, ex.getCode());
        verify(userRepository).incrementFailedLogins(1L);
        verify(sessionService, never()).issue(any());
    }

    @Test
    void login_unknownAccount_isTheSameNeutral401_andStillSpendsHashingTime() {
        when(userLookup.findUser("ghost@example.com")).thenReturn(Optional.empty());

        AuthException ex = thrown(() -> authService.login(login("ghost@example.com", "whatever-pass"), "10.0.0.1"));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
        assertEquals(AuthCodes.CREDENTIALS_INVALID, ex.getCode());
        verify(passwordEncoder, atLeastOnce()).matches(eq("whatever-pass"), anyString());
    }

    @Test
    void login_lockedAccount_is429WithRetryAfter_evenForTheRightPassword() {
        user.setLockedUntil(Instant.now().plusSeconds(120));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        AuthException ex = thrown(() -> authService.login(login("donor@example.com", "right-pass-123"), "10.0.0.1"));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getStatus());
        assertEquals(AuthCodes.ACCOUNT_LOCKED, ex.getCode());
        assertNotNull(ex.getRetryAfterSeconds());
        assertTrue(ex.getRetryAfterSeconds() > 100 && ex.getRetryAfterSeconds() <= 121);
        verify(passwordEncoder, never()).matches(eq("right-pass-123"), eq("$2a$12$hash"));
        verify(sessionService, never()).issue(any());
    }

    @Test
    void login_suspendedAccount_isOnlyRevealedAfterTheCorrectPassword() {
        user.setStatus(UserStatus.SUSPENDED);

        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);
        assertEquals(AuthCodes.CREDENTIALS_INVALID, thrown(() -> authService.login(login("donor@example.com", "wrong"), "ip")).getCode());

        when(passwordEncoder.matches("right-pass-123", "$2a$12$hash")).thenReturn(true);
        AuthException ex = thrown(() -> authService.login(login("donor@example.com", "right-pass-123"), "ip"));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertEquals(AuthCodes.ACCOUNT_SUSPENDED, ex.getCode());
    }

    @Test
    void login_accountWithoutUsablePassword_cannotSignInWithAPassword() {
        user.setPasswordHash("!disabled-demo-account");

        AuthException ex = thrown(() -> authService.login(login("donor@example.com", "!disabled-demo-account"), "ip"));

        assertEquals(AuthCodes.CREDENTIALS_INVALID, ex.getCode());
        verify(sessionService, never()).issue(any());
    }

    // ---- reset by phone (F-02) -------------------------------------------------------------------------------

    private ResetPasswordDto resetDto() {
        ResetPasswordDto dto = new ResetPasswordDto();
        dto.setPhoneNumber("0712345678");
        dto.setNewPassword("brand-new-pass-77");
        return dto;
    }

    @Test
    void resetPassword_verifiedPhone_setsPassword_unlocks_andSignsOutEverywhere() {
        when(otpService.isPhoneVerified("+94712345678")).thenReturn(true);
        when(userRepository.findFirstByPhoneNumberIn(any())).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("brand-new-pass-77")).thenReturn("hashed");

        authService.resetPassword(resetDto(), "ip");

        assertEquals("hashed", user.getPasswordHash());
        verify(userRepository).clearLoginFailures(1L);
        verify(sessionService).revokeAll(user);
        verify(otpService).consume("+94712345678");
    }

    @Test
    void resetPassword_refusesTheDisabledDemoMarker() {
        user.setPasswordHash("!disabled-demo-account");
        when(otpService.isPhoneVerified("+94712345678")).thenReturn(true);
        when(userRepository.findFirstByPhoneNumberIn(any())).thenReturn(Optional.of(user));

        AuthException ex = thrown(() -> authService.resetPassword(resetDto(), "ip"));

        assertEquals(AuthCodes.RESET_NOT_ALLOWED, ex.getCode());
        verify(userRepository, never()).save(any());
    }

    @Test
    void resetPassword_refusesFlaggedDemoAccounts_andSuspendedOnes_andGoogleOnlyOnes() {
        when(otpService.isPhoneVerified("+94712345678")).thenReturn(true);
        when(userRepository.findFirstByPhoneNumberIn(any())).thenReturn(Optional.of(user));

        user.setDemoAccount(true);
        assertEquals(AuthCodes.RESET_NOT_ALLOWED, thrown(() -> authService.resetPassword(resetDto(), "ip")).getCode());

        user.setDemoAccount(false);
        user.setStatus(UserStatus.SUSPENDED);
        assertEquals(AuthCodes.RESET_NOT_ALLOWED, thrown(() -> authService.resetPassword(resetDto(), "ip")).getCode());

        user.setStatus(UserStatus.ACTIVE);
        user.setPasswordHash(null);
        assertEquals(AuthCodes.RESET_NOT_ALLOWED, thrown(() -> authService.resetPassword(resetDto(), "ip")).getCode());
        verify(userRepository, never()).save(any());
    }

    @Test
    void resetPassword_unverifiedPhone_isRejected() {
        when(otpService.isPhoneVerified("+94712345678")).thenReturn(false);

        AuthException ex = thrown(() -> authService.resetPassword(resetDto(), "ip"));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("auth.phone.notVerified", ex.getFieldErrors().get("phoneNumber"));
    }

    @Test
    void resetPassword_weakPassword_isRejectedWithAFieldCode() {
        when(otpService.isPhoneVerified("+94712345678")).thenReturn(true);
        when(userRepository.findFirstByPhoneNumberIn(any())).thenReturn(Optional.of(user));
        ResetPasswordDto dto = resetDto();
        dto.setNewPassword("password123");

        AuthException ex = thrown(() -> authService.resetPassword(dto, "ip"));

        assertEquals(AuthCodes.PASSWORD_COMMON, ex.getFieldErrors().get("newPassword"));
    }

    // ---- reset by email ----------------------------------------------------------------------------------------

    @Test
    void forgotPassword_unknownEmail_doesNothingAndSaysNothing() {
        when(userRepository.findByEmailIgnoreCase("ghost@example.com")).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> authService.forgotPassword("ghost@example.com", "ip"));

        verifyNoInteractions(emailService);
        verify(tokenService, never()).issuePasswordReset(any());
    }

    @Test
    void forgotPassword_knownEmail_sendsOneLink_andCooldownSuppressesTheNext() {
        when(userRepository.findByEmailIgnoreCase("donor@example.com")).thenReturn(Optional.of(user));
        when(tokenService.cooldownRemainingSeconds(1L, AuthTokenType.PASSWORD_RESET)).thenReturn(0);
        when(tokenService.issuePasswordReset(1L)).thenReturn("raw-token");

        authService.forgotPassword("donor@example.com", "ip");
        verify(emailService).sendPasswordReset(user, "raw-token");

        when(tokenService.cooldownRemainingSeconds(1L, AuthTokenType.PASSWORD_RESET)).thenReturn(30);
        authService.forgotPassword("donor@example.com", "ip");
        verify(emailService, times(1)).sendPasswordReset(any(), anyString());
    }

    @Test
    void forgotPassword_neverIssuesALinkForDemoOrPasswordlessAccounts() {
        when(userRepository.findByEmailIgnoreCase("donor@example.com")).thenReturn(Optional.of(user));
        user.setDemoAccount(true);

        authService.forgotPassword("donor@example.com", "ip");

        verifyNoInteractions(emailService);
    }

    private AuthToken resetToken() {
        AuthToken t = new AuthToken();
        t.setType(AuthTokenType.PASSWORD_RESET);
        t.setUserId(1L);
        return t;
    }

    @Test
    void resetWithToken_weakPassword_doesNotSpendTheToken() {
        when(tokenService.peek(AuthTokenType.PASSWORD_RESET, "tok")).thenReturn(Optional.of(resetToken()));
        ResetPasswordTokenDto dto = new ResetPasswordTokenDto();
        dto.setToken("tok");
        dto.setNewPassword("1234567890");

        AuthException ex = thrown(() -> authService.resetPasswordWithToken(dto, "ip"));

        assertEquals(AuthCodes.PASSWORD_COMMON, ex.getFieldErrors().get("newPassword"));
        verify(tokenService, never()).consume(any(), any());
    }

    @Test
    void resetWithToken_success_marksEmailVerified_andRevokesSessions() {
        when(tokenService.peek(AuthTokenType.PASSWORD_RESET, "tok")).thenReturn(Optional.of(resetToken()));
        when(tokenService.consume(AuthTokenType.PASSWORD_RESET, "tok")).thenReturn(Optional.of(resetToken()));
        ResetPasswordTokenDto dto = new ResetPasswordTokenDto();
        dto.setToken("tok");
        dto.setNewPassword("a-long-unique-passphrase");

        authService.resetPasswordWithToken(dto, "ip");

        assertTrue(user.isEmailVerified());
        assertEquals("encoded", user.getPasswordHash());
        verify(sessionService).revokeAll(user);
        verify(userRepository).clearLoginFailures(1L);
    }

    @Test
    void resetWithToken_unknownOrSpentToken_is400() {
        when(tokenService.peek(any(), anyString())).thenReturn(Optional.empty());
        ResetPasswordTokenDto dto = new ResetPasswordTokenDto();
        dto.setToken("spent");
        dto.setNewPassword("a-long-unique-passphrase");

        assertEquals(AuthCodes.RESET_TOKEN_INVALID, thrown(() -> authService.resetPasswordWithToken(dto, "ip")).getCode());
    }

    // ---- email verification ------------------------------------------------------------------------------------

    @Test
    void verifyEmail_validToken_marksTheAddressVerified() {
        AuthToken t = new AuthToken();
        t.setUserId(1L);
        when(tokenService.consume(AuthTokenType.EMAIL_VERIFY, "tok")).thenReturn(Optional.of(t));

        authService.verifyEmail("tok", "ip");

        assertTrue(user.isEmailVerified());
        verify(userRepository).save(user);
    }

    @Test
    void verifyEmail_invalidToken_is400() {
        when(tokenService.consume(any(), any())).thenReturn(Optional.empty());

        assertEquals(AuthCodes.EMAIL_VERIFY_TOKEN_INVALID, thrown(() -> authService.verifyEmail("bad", "ip")).getCode());
    }

    @Test
    void resendVerification_respectsTheCooldown_andRefusesVerifiedAddresses() {
        when(tokenService.cooldownRemainingSeconds(1L, AuthTokenType.EMAIL_VERIFY)).thenReturn(42);
        AuthException cooldown = thrown(() -> authService.resendEmailVerification(1L));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, cooldown.getStatus());
        assertEquals(42, cooldown.getRetryAfterSeconds());

        user.setEmailVerifiedAt(Instant.now());
        assertEquals(HttpStatus.CONFLICT, thrown(() -> authService.resendEmailVerification(1L)).getStatus());

        user.setEmailVerifiedAt(null);
        when(tokenService.cooldownRemainingSeconds(1L, AuthTokenType.EMAIL_VERIFY)).thenReturn(0);
        when(tokenService.issueEmailVerification(1L)).thenReturn("fresh");
        authService.resendEmailVerification(1L);
        verify(emailService).sendEmailVerification(user, "fresh");
    }

    // ---- logout ------------------------------------------------------------------------------------------------

    @Test
    void logout_withAKnownUser_signsOutEverywhere_otherwiseEndsOnlyTheCookieSession() {
        authService.logout(1L, "cookie", "ip");
        verify(sessionService).revokeAll(user);

        when(sessionService.ownerOf("cookie")).thenReturn(Optional.of(user));
        authService.logout(null, "cookie", "ip");
        verify(sessionService).revokeToken("cookie");
        verify(sessionService, times(1)).revokeAll(any());
    }

    @Test
    void sha256Hex_isStable_andHidesTheInput() {
        assertEquals(64, AuthService.sha256Hex("x").length());
        assertEquals(AuthService.sha256Hex("x"), AuthService.sha256Hex("x"));
        assertFalse(AuthService.sha256Hex("secret-token").contains("secret"));
        assertEquals(List.of(), List.of());
    }
}
