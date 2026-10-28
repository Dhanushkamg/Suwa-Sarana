package com.suwasarana.api.auth;

import com.suwasarana.api.audit.AdminAuditService;
import com.suwasarana.api.audit.AuditAction;
import com.suwasarana.api.audit.AuditLogService;
import com.suwasarana.api.auth.dto.AcceptInviteDto;
import com.suwasarana.api.auth.dto.AuthResponse;
import com.suwasarana.api.auth.dto.LoginDto;
import com.suwasarana.api.auth.dto.RegisterDto;
import com.suwasarana.api.auth.dto.ResetPasswordDto;
import com.suwasarana.api.auth.dto.ResetPasswordTokenDto;
import com.suwasarana.api.auth.otp.OtpService;
import com.suwasarana.api.auth.token.AuthToken;
import com.suwasarana.api.auth.token.AuthTokenService;
import com.suwasarana.api.auth.token.AuthTokenType;
import com.suwasarana.api.email.EmailService;
import com.suwasarana.api.security.CustomUserDetailsService;
import com.suwasarana.api.user.PhoneNumbers;
import com.suwasarana.api.user.Role;
import com.suwasarana.api.user.User;
import com.suwasarana.api.user.UserRepository;
import com.suwasarana.api.user.UserStatus;
import com.suwasarana.api.user.VerificationStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Password-based authentication: sign-up, login (with per-account lockout), logout, refresh, password reset by phone
 * OTP or by emailed link, email verification and administrator invitations. Google sign-in is in
 * {@link GoogleAuthService}; account creation rules live in {@link RegistrationService}.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final CustomUserDetailsService userLookup;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final RegistrationService registrationService;
    private final SessionService sessionService;
    private final LoginAttemptService loginAttempts;
    private final OtpService otpService;
    private final AuthTokenService tokenService;
    private final EmailService emailService;
    private final AuditLogService auditLog;
    private final AdminAuditService adminAudit;

    /** Verified against when the account does not exist, so "no such account" and "wrong password" take equally long. */
    private volatile String dummyHash;

    public AuthService(UserRepository userRepository, CustomUserDetailsService userLookup, PasswordEncoder passwordEncoder,
                       PasswordPolicy passwordPolicy, RegistrationService registrationService, SessionService sessionService,
                       LoginAttemptService loginAttempts, OtpService otpService, AuthTokenService tokenService,
                       EmailService emailService, AuditLogService auditLog, AdminAuditService adminAudit) {
        this.userRepository = userRepository;
        this.userLookup = userLookup;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.registrationService = registrationService;
        this.sessionService = sessionService;
        this.loginAttempts = loginAttempts;
        this.otpService = otpService;
        this.tokenService = tokenService;
        this.emailService = emailService;
        this.auditLog = auditLog;
        this.adminAudit = adminAudit;
    }

    // ---- sign-up ----------------------------------------------------------------------------------------

    public AuthResponse register(RegisterDto dto, String ipAddress) {
        RegistrationService.NewAccount account = new RegistrationService.NewAccount();
        account.email = dto.getEmail();
        account.phoneNumber = dto.getPhoneNumber();
        account.firstName = dto.getFirstName();
        account.lastName = dto.getLastName();
        account.role = dto.getRole();
        account.nic = dto.getNicNumber();
        account.password = dto.getPassword();
        account.preferredLanguage = dto.getPreferredLanguage();
        account.consentGiven = Boolean.TRUE.equals(dto.getAcceptTerms());
        // Institutions (hospital / blood bank) stay PENDING until an administrator verifies them: see VerificationGuard.

        User user = registrationService.create(account);
        sendEmailVerification(user);
        AuthResponse response = sessionService.issue(user);
        auditLog.logRegistration(user.getId(), user.getEmail(), user.getRole().name(), ipAddress);
        return response;
    }

    // ---- login ------------------------------------------------------------------------------------------

    public AuthResponse login(LoginDto dto, String ipAddress) {
        String identifier = dto.getUsername() == null ? "" : dto.getUsername().trim();
        Optional<User> found = userLookup.findUser(identifier);

        if (found.isEmpty()) {
            passwordEncoder.matches(dto.getPassword(), dummyHash()); // equalise timing
            auditLog.logLoginFailure(identifier, ipAddress, "unknown account");
            throw AuthException.unauthorized(AuthCodes.CREDENTIALS_INVALID);
        }
        User user = found.get();

        loginAttempts.assertNotLocked(user, ipAddress);

        boolean passwordOk = user.hasUsablePassword() && passwordEncoder.matches(dto.getPassword(), user.getPasswordHash());
        if (!user.hasUsablePassword()) {
            passwordEncoder.matches(dto.getPassword(), dummyHash());
        }
        if (!passwordOk) {
            loginAttempts.recordFailure(user);
            auditLog.logLoginFailure(identifier, ipAddress, "bad credentials");
            throw AuthException.unauthorized(AuthCodes.CREDENTIALS_INVALID);
        }
        // The password was right, so it is safe to say why the account cannot be used.
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw AuthException.forbidden(AuthCodes.ACCOUNT_SUSPENDED);
        }
        if (user.getStatus() == UserStatus.DELETED) {
            throw AuthException.unauthorized(AuthCodes.CREDENTIALS_INVALID);
        }

        loginAttempts.recordSuccess(user);
        AuthResponse response = sessionService.issue(user);
        auditLog.logLoginSuccess(user.getId(), user.getEmail(), ipAddress);
        return response;
    }

    // ---- sessions ---------------------------------------------------------------------------------------

    public AuthResponse refreshToken(String rawRefreshToken, String ipAddress) {
        return sessionService.rotate(rawRefreshToken, ipAddress);
    }

    /**
     * Signs out. A known user (valid access token) is signed out of every device; otherwise only the session behind the
     * refresh-token cookie is ended, so an expired access token can still log out.
     */
    public void logout(Long userId, String rawRefreshToken, String ipAddress) {
        if (userId != null) {
            userRepository.findById(userId).ifPresent(sessionService::revokeAll);
            auditLog.logLogout(userId, ipAddress);
            return;
        }
        sessionService.ownerOf(rawRefreshToken).ifPresent(owner -> auditLog.logLogout(owner.getId(), ipAddress));
        sessionService.revokeToken(rawRefreshToken);
    }

    // ---- password reset by phone OTP --------------------------------------------------------------------

    /** Sets a new password for the account behind a phone number that has just passed OTP verification. */
    public void resetPassword(ResetPasswordDto dto, String ipAddress) {
        String phone = PhoneNumbers.normalize(dto.getPhoneNumber());
        if (phone == null || !otpService.isPhoneVerified(phone)) {
            throw AuthException.invalidField("phoneNumber", AuthCodes.PHONE_NOT_VERIFIED);
        }
        User user = userRepository.findFirstByPhoneNumberIn(PhoneNumbers.variants(phone))
                .orElseThrow(() -> AuthException.badRequest(AuthCodes.RESET_NOT_ALLOWED));
        assertCanResetPassword(user);

        passwordPolicy.enforce("newPassword", dto.getNewPassword(), user.getEmail(), user.getPhoneNumber());
        applyNewPassword(user, dto.getNewPassword(), ipAddress);
        otpService.consume(phone);
    }

    // ---- password reset by emailed link -----------------------------------------------------------------

    /** Always completes silently: whether the address has an account is never revealed. */
    public void forgotPassword(String email, String ipAddress) {
        Optional<User> found = userRepository.findByEmailIgnoreCase(email == null ? "" : email.trim());
        if (found.isEmpty()) {
            return;
        }
        User user = found.get();
        if (!canResetPassword(user)) {
            return;
        }
        if (tokenService.cooldownRemainingSeconds(user.getId(), AuthTokenType.PASSWORD_RESET) > 0) {
            return;
        }
        String token = tokenService.issuePasswordReset(user.getId());
        emailService.sendPasswordReset(user, token);
        auditLog.logSecurityEvent("PASSWORD_RESET_REQUESTED", user.getId(), ipAddress);
    }

    public void resetPasswordWithToken(ResetPasswordTokenDto dto, String ipAddress) {
        AuthToken peeked = tokenService.peek(AuthTokenType.PASSWORD_RESET, dto.getToken())
                .orElseThrow(() -> AuthException.badRequest(AuthCodes.RESET_TOKEN_INVALID));
        User user = userRepository.findById(peeked.getUserId())
                .orElseThrow(() -> AuthException.badRequest(AuthCodes.RESET_TOKEN_INVALID));
        assertCanResetPassword(user);
        // Check the password BEFORE spending the token, so a too-weak password does not burn the link.
        passwordPolicy.enforce("newPassword", dto.getNewPassword(), user.getEmail(), user.getPhoneNumber());

        if (tokenService.consume(AuthTokenType.PASSWORD_RESET, dto.getToken()).isEmpty()) {
            throw AuthException.badRequest(AuthCodes.RESET_TOKEN_INVALID);
        }
        if (!user.isEmailVerified()) {
            user.setEmailVerifiedAt(Instant.now()); // the link reached the inbox, which proves the address
            userRepository.save(user);
        }
        applyNewPassword(user, dto.getNewPassword(), ipAddress);
    }

    private boolean canResetPassword(User user) {
        // A usable password is required: this refuses the disabled-demo marker ("!...") and Google-only accounts.
        return user.isActive() && user.hasUsablePassword() && !user.isDemoAccount();
    }

    private void assertCanResetPassword(User user) {
        if (!canResetPassword(user)) {
            throw AuthException.badRequest(AuthCodes.RESET_NOT_ALLOWED);
        }
    }

    /** New password: unlocks the account and signs out every device (refresh tokens + outstanding access tokens). */
    void applyNewPassword(User user, String newPassword, String ipAddress) {
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        userRepository.clearLoginFailures(user.getId());
        sessionService.revokeAll(user);
        auditLog.logPasswordReset(user.getId(), ipAddress);
    }

    // ---- email verification -----------------------------------------------------------------------------

    public void verifyEmail(String rawToken, String ipAddress) {
        AuthToken token = tokenService.consume(AuthTokenType.EMAIL_VERIFY, rawToken)
                .orElseThrow(() -> AuthException.badRequest(AuthCodes.EMAIL_VERIFY_TOKEN_INVALID));
        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> AuthException.badRequest(AuthCodes.EMAIL_VERIFY_TOKEN_INVALID));
        if (!user.isEmailVerified()) {
            user.setEmailVerifiedAt(Instant.now());
            userRepository.save(user);
            auditLog.logSecurityEvent("EMAIL_VERIFIED", user.getId(), ipAddress);
        }
    }

    /** Sends a fresh verification link to the signed-in user, at most once per cooldown. */
    public void resendEmailVerification(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> AuthException.unauthorized(AuthCodes.NOT_SIGNED_IN));
        if (user.isEmailVerified()) {
            throw AuthException.conflict(AuthCodes.EMAIL_ALREADY_VERIFIED);
        }
        int wait = tokenService.cooldownRemainingSeconds(user.getId(), AuthTokenType.EMAIL_VERIFY);
        if (wait > 0) {
            throw AuthException.tooManyRequests(AuthCodes.EMAIL_VERIFY_COOLDOWN, wait);
        }
        sendEmailVerification(user);
    }

    private void sendEmailVerification(User user) {
        if (user.isEmailVerified()) {
            return;
        }
        emailService.sendEmailVerification(user, tokenService.issueEmailVerification(user.getId()));
    }

    // ---- administrator invitation -----------------------------------------------------------------------

    /** Creates the ADMIN account for a valid invitation. The email comes from the invitation, never from the client. */
    public AuthResponse acceptInvite(AcceptInviteDto dto, String ipAddress) {
        AuthToken invite = tokenService.peek(AuthTokenType.ADMIN_INVITE, dto.getToken())
                .orElseThrow(() -> AuthException.badRequest(AuthCodes.INVITE_INVALID));

        RegistrationService.NewAccount account = new RegistrationService.NewAccount();
        account.email = invite.getEmail();
        account.phoneNumber = dto.getPhoneNumber();
        account.firstName = dto.getFirstName();
        account.lastName = dto.getLastName();
        account.role = Role.ADMIN;
        account.allowAdmin = true;
        account.password = dto.getPassword();
        account.emailVerified = true; // the invitation reached this inbox
        account.verificationStatus = VerificationStatus.VERIFIED;
        account.preferredLanguage = dto.getPreferredLanguage();
        account.consentGiven = Boolean.TRUE.equals(dto.getAcceptTerms());

        // Validate everything first (policy, phone, duplicates) and only then spend the single-use token.
        passwordPolicy.enforce("password", dto.getPassword(), invite.getEmail(), dto.getPhoneNumber());
        if (!otpService.isPhoneVerified(dto.getPhoneNumber())) {
            throw AuthException.invalidField("phoneNumber", AuthCodes.PHONE_NOT_VERIFIED);
        }
        if (tokenService.consume(AuthTokenType.ADMIN_INVITE, dto.getToken()).isEmpty()) {
            throw AuthException.badRequest(AuthCodes.INVITE_INVALID);
        }
        User admin = registrationService.create(account);

        adminAudit.record(admin.getId(), Role.ADMIN, AuditAction.ADMIN_INVITE_ACCEPTED, admin.getId(),
                null, "invited by user " + invite.getCreatedBy(), ipAddress);
        log.info("Administrator account {} created from an invitation", admin.getId());
        return sessionService.issue(admin);
    }

    // ---- helpers ----------------------------------------------------------------------------------------

    private String dummyHash() {
        String h = dummyHash;
        if (h == null) {
            h = passwordEncoder.encode("dummy-password-for-timing-equalisation");
            dummyHash = h;
        }
        return h;
    }

    /**
     * Hashes a raw token string with SHA-256, returning a lowercase hex string.
     * MD5 is cryptographically broken and must NOT be used for security tokens.
     */
    public static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the JVM spec — this path is unreachable
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
