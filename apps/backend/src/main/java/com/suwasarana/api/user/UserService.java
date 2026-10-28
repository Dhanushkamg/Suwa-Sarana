package com.suwasarana.api.user;

import com.suwasarana.api.audit.AuditLogService;
import com.suwasarana.api.auth.AuthCodes;
import com.suwasarana.api.auth.AuthException;
import com.suwasarana.api.auth.AuthMessages;
import com.suwasarana.api.auth.AuthProperties;
import com.suwasarana.api.auth.LoginAttemptService;
import com.suwasarana.api.auth.PasswordPolicy;
import com.suwasarana.api.auth.SessionService;
import com.suwasarana.api.auth.dto.AuthResponse;
import com.suwasarana.api.auth.otp.OtpService;
import com.suwasarana.api.user.dto.ChangePasswordDto;
import com.suwasarana.api.user.dto.MeDto;
import com.suwasarana.api.user.dto.UpdateMeDto;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Locale;

/** Self-service account management: profile, phone change (OTP), password change, consent. */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final OtpService otpService;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final LoginAttemptService loginAttempts;
    private final SessionService sessionService;
    private final AuthProperties properties;
    private final AuditLogService auditLog;

    public UserService(UserRepository userRepository, OtpService otpService, PasswordEncoder passwordEncoder,
                       PasswordPolicy passwordPolicy, LoginAttemptService loginAttempts, SessionService sessionService,
                       AuthProperties properties, AuditLogService auditLog) {
        this.userRepository = userRepository;
        this.otpService = otpService;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.loginAttempts = loginAttempts;
        this.sessionService = sessionService;
        this.properties = properties;
        this.auditLog = auditLog;
    }

    public MeDto me(Long userId) {
        return MeDto.of(load(userId), properties.getConsentVersion());
    }

    public MeDto update(Long userId, UpdateMeDto dto) {
        User user = load(userId);

        if (dto.getFirstName() != null) {
            user.setFirstName(dto.getFirstName());
        }
        if (dto.getLastName() != null) {
            user.setLastName(dto.getLastName());
        }
        if (dto.getPreferredLanguage() != null) {
            if (!AuthMessages.isSupported(dto.getPreferredLanguage())) {
                throw AuthException.invalidField("preferredLanguage", AuthCodes.LANGUAGE_INVALID);
            }
            user.setPreferredLanguage(dto.getPreferredLanguage());
        }

        String newPhone = PhoneNumbers.normalize(dto.getPhoneNumber());
        if (dto.getPhoneNumber() != null && newPhone == null) {
            throw AuthException.invalidField("phoneNumber", AuthCodes.PHONE_INVALID);
        }
        boolean phoneChanged = newPhone != null && !newPhone.equals(user.getPhoneNumber());
        if (phoneChanged) {
            if (!otpService.isPhoneVerified(newPhone)) {
                throw AuthException.invalidField("phoneNumber", AuthCodes.PHONE_NOT_VERIFIED);
            }
            if (userRepository.existsByPhoneNumberInAndIdNot(PhoneNumbers.variants(newPhone), userId)) {
                throw AuthException.conflict(AuthCodes.PHONE_IN_USE);
            }
            user.setPhoneNumber(newPhone);
        }

        try {
            user = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw AuthException.conflict(AuthCodes.PHONE_IN_USE);
        }
        if (phoneChanged) {
            otpService.consume(newPhone);
        }
        return MeDto.of(user, properties.getConsentVersion());
    }

    /**
     * Changes the password of a password account. Every other session is signed out (refresh tokens deleted, access tokens
     * invalidated) and a fresh session is returned for the caller.
     */
    public AuthResponse changePassword(Long userId, ChangePasswordDto dto, String ipAddress) {
        User user = load(userId);
        if (!user.hasUsablePassword()) {
            throw AuthException.badRequest(AuthCodes.RESET_NOT_ALLOWED); // Google-only / disabled accounts have no password to change
        }
        loginAttempts.assertNotLocked(user, ipAddress);
        if (!passwordEncoder.matches(dto.getCurrentPassword(), user.getPasswordHash())) {
            loginAttempts.recordFailure(user);
            throw AuthException.invalidField("currentPassword", AuthCodes.PASSWORD_WRONG);
        }
        passwordPolicy.enforce("newPassword", dto.getNewPassword(), user.getEmail(), user.getPhoneNumber());

        user.setPasswordHash(passwordEncoder.encode(dto.getNewPassword()));
        userRepository.save(user);
        userRepository.clearLoginFailures(userId);
        sessionService.revokeAll(user);
        auditLog.logPasswordChange(userId, ipAddress);
        return sessionService.issue(load(userId)); // reload: the token version just moved
    }

    /** Sends a fresh code to the user's own (verified) phone, e.g. to confirm account deletion. */
    public void sendOwnOtp(Long userId, Locale locale) {
        otpService.generateAndSendOtp(load(userId).getPhoneNumber(), locale);
    }

    /** Records acceptance of the CURRENT terms / privacy version. */
    public MeDto acceptCurrentConsent(Long userId) {
        User user = load(userId);
        user.setConsentVersion(properties.getConsentVersion());
        user.setConsentedAt(Instant.now());
        return MeDto.of(userRepository.save(user), properties.getConsentVersion());
    }

    private User load(Long userId) {
        return userRepository.findById(userId).orElseThrow(() -> AuthException.unauthorized(AuthCodes.NOT_SIGNED_IN));
    }
}
