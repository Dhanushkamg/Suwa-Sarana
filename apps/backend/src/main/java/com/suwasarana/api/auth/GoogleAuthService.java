package com.suwasarana.api.auth;

import com.suwasarana.api.audit.AdminAuditService;
import com.suwasarana.api.audit.AuditAction;
import com.suwasarana.api.audit.AuditLogService;
import com.suwasarana.api.auth.GoogleIdentityVerifier.GoogleIdentity;
import com.suwasarana.api.auth.dto.AuthResponse;
import com.suwasarana.api.auth.dto.GoogleLinkRequest;
import com.suwasarana.api.auth.dto.GoogleRegisterRequest;
import com.suwasarana.api.auth.dto.GoogleVerifyRequest;
import com.suwasarana.api.auth.dto.GoogleVerifyResponse;
import com.suwasarana.api.user.AuthProvider;
import com.suwasarana.api.user.User;
import com.suwasarana.api.user.UserRepository;
import com.suwasarana.api.user.UserStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

/**
 * Sign-in with Google. Google proves the email address, never a phone number, so a first-time user still completes
 * role + phone OTP + consent through {@link #register}.
 *
 * Linking rules (why): a password account created with someone else's address can exist, because password sign-up
 * does not prove ownership of the email until the link in the verification mail is opened (pre-hijack).
 *  - email NOT verified on the local account: the Google user is the real owner. The local password is dropped, every
 *    session is revoked, the account becomes a GOOGLE account. The squatter keeps no way in: no password, no tokens,
 *    and phone-OTP password reset refuses accounts without a password.
 *  - email verified on the local account: the Google user may be a different person. We NEVER link silently; the
 *    client is told {@code requiresLinking} and must call {@link #link} with the account password.
 */
@Service
public class GoogleAuthService {

    private final GoogleIdentityVerifier verifier;
    private final UserRepository userRepository;
    private final SessionService sessionService;
    private final RegistrationService registrationService;
    private final LoginAttemptService loginAttempts;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLog;
    private final AdminAuditService adminAudit;

    public GoogleAuthService(GoogleIdentityVerifier verifier, UserRepository userRepository, SessionService sessionService,
                             RegistrationService registrationService, LoginAttemptService loginAttempts,
                             PasswordEncoder passwordEncoder, AuditLogService auditLog, AdminAuditService adminAudit) {
        this.verifier = verifier;
        this.userRepository = userRepository;
        this.sessionService = sessionService;
        this.registrationService = registrationService;
        this.loginAttempts = loginAttempts;
        this.passwordEncoder = passwordEncoder;
        this.auditLog = auditLog;
        this.adminAudit = adminAudit;
    }

    public GoogleVerifyResponse verify(GoogleVerifyRequest request, String ipAddress) {
        GoogleIdentity identity = verifier.verify(request.getIdToken());

        Optional<User> byProvider = userRepository.findByProviderId(identity.subject());
        if (byProvider.isPresent()) {
            return signedIn(byProvider.get(), ipAddress);
        }

        Optional<User> byEmail = userRepository.findByEmailIgnoreCase(identity.email());
        if (byEmail.isEmpty()) {
            return new GoogleVerifyResponse(true, identity.email(), null);
        }
        User user = byEmail.get();
        rejectUnusable(user);

        if (user.getAuthProvider() == AuthProvider.GOOGLE) {
            // Already a Google account but under a different Google subject: refuse rather than guess.
            throw AuthException.conflict(AuthCodes.GOOGLE_ACCOUNT_MISMATCH);
        }

        if (!user.isEmailVerified()) {
            takeOverUnverifiedLocalAccount(user, identity, ipAddress);
            return signedIn(userRepository.findById(user.getId()).orElseThrow(), ipAddress);
        }
        return new GoogleVerifyResponse(false, true, identity.email(), null);
    }

    /** Explicit link of a Google identity to a verified password account; requires the account password. */
    public AuthResponse link(GoogleLinkRequest request, String ipAddress) {
        GoogleIdentity identity = verifier.verify(request.getIdToken());
        User user = userRepository.findByEmailIgnoreCase(identity.email())
                .orElseThrow(() -> AuthException.unauthorized(AuthCodes.CREDENTIALS_INVALID));
        rejectUnusable(user);
        loginAttempts.assertNotLocked(user, ipAddress);

        if (!user.hasUsablePassword() || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            loginAttempts.recordFailure(user);
            throw AuthException.unauthorized(AuthCodes.CREDENTIALS_INVALID);
        }
        Optional<User> other = userRepository.findByProviderId(identity.subject());
        if (other.isPresent() && !other.get().getId().equals(user.getId())) {
            throw AuthException.conflict(AuthCodes.GOOGLE_ACCOUNT_MISMATCH);
        }

        loginAttempts.recordSuccess(user);
        user.setProviderId(identity.subject());
        if (!user.isEmailVerified()) {
            user.setEmailVerifiedAt(Instant.now()); // Google verified the same address
        }
        userRepository.save(user);
        auditLog.logSecurityEvent("GOOGLE_LINKED", user.getId(), ipAddress);
        return sessionService.issue(userRepository.findById(user.getId()).orElseThrow());
    }

    /** Second step for a first-time Google user. */
    public AuthResponse register(GoogleRegisterRequest request, String ipAddress) {
        GoogleIdentity identity = verifier.verify(request.getIdToken());

        RegistrationService.NewAccount account = new RegistrationService.NewAccount();
        account.email = identity.email();
        account.phoneNumber = request.getPhoneNumber();
        account.firstName = request.getFirstName();
        account.lastName = request.getLastName();
        account.role = request.getRole();
        account.nic = request.getNicNumber();
        account.provider = AuthProvider.GOOGLE;
        account.providerId = identity.subject();
        account.emailVerified = true;
        account.preferredLanguage = request.getPreferredLanguage();
        account.consentGiven = Boolean.TRUE.equals(request.getAcceptTerms());

        User user = registrationService.create(account);
        auditLog.logRegistration(user.getId(), user.getEmail(), user.getRole().name(), ipAddress);
        return sessionService.issue(user);
    }

    // ---- helpers ----------------------------------------------------------------------------------------

    private void takeOverUnverifiedLocalAccount(User user, GoogleIdentity identity, String ipAddress) {
        user.setPasswordHash(null);
        user.setAuthProvider(AuthProvider.GOOGLE);
        user.setProviderId(identity.subject());
        user.setEmailVerifiedAt(Instant.now());
        userRepository.save(user);
        userRepository.clearLoginFailures(user.getId());
        sessionService.revokeAll(user);
        adminAudit.record(null, null, AuditAction.GOOGLE_ACCOUNT_TAKEOVER, user.getId(), null,
                "unverified password account claimed by its Google owner; password dropped, sessions revoked", ipAddress);
    }

    private GoogleVerifyResponse signedIn(User user, String ipAddress) {
        rejectUnusable(user);
        AuthResponse response = sessionService.issue(user);
        auditLog.logLoginSuccess(user.getId(), user.getEmail(), ipAddress);
        return new GoogleVerifyResponse(false, user.getEmail(), response);
    }

    private void rejectUnusable(User user) {
        if (user.isDemoAccount()) {
            throw AuthException.unauthorized(AuthCodes.GOOGLE_INVALID);
        }
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw AuthException.forbidden(AuthCodes.ACCOUNT_SUSPENDED);
        }
        if (user.getStatus() == UserStatus.DELETED) {
            throw AuthException.unauthorized(AuthCodes.GOOGLE_INVALID);
        }
    }
}
