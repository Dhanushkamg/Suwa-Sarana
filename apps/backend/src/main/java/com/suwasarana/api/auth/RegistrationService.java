package com.suwasarana.api.auth;

import com.suwasarana.api.auth.otp.OtpService;
import com.suwasarana.api.email.EmailService;
import com.suwasarana.api.user.AuthProvider;
import com.suwasarana.api.user.NicHasher;
import com.suwasarana.api.user.NicValidator;
import com.suwasarana.api.user.PhoneNumbers;
import com.suwasarana.api.user.Role;
import com.suwasarana.api.user.User;
import com.suwasarana.api.user.UserRepository;
import com.suwasarana.api.user.VerificationStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

/**
 * The one place accounts are created (password sign-up, Google sign-up, admin invitation), so every path applies the
 * same rules: allowed role, password policy, NIC format + uniqueness, phone proven by OTP, neutral duplicate handling,
 * recorded consent, and 409 (never 500) when two sign-ups race.
 *
 * Deliberately NOT transactional: the final insert runs in its own transaction so a unique-constraint failure can be
 * caught here and turned into a 409.
 */
@Service
public class RegistrationService {

    private static final Logger log = LoggerFactory.getLogger(RegistrationService.class);

    /** What is needed to create an account. */
    public static class NewAccount {
        public String email;
        public String phoneNumber;
        public String firstName;
        public String lastName;
        public Role role;
        public String nic;
        /** Plain password; null for Google accounts. */
        public String password;
        public AuthProvider provider = AuthProvider.LOCAL;
        public String providerId;
        public boolean emailVerified;
        public String preferredLanguage;
        /** Records consent_version / consented_at. Required at the API layer by @AssertTrue acceptTerms. */
        public boolean consentGiven;
        /** Only the admin-invitation path may create an ADMIN. */
        public boolean allowAdmin;
        public VerificationStatus verificationStatus = VerificationStatus.PENDING;
    }

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final OtpService otpService;
    private final NicHasher nicHasher;
    private final EmailService emailService;
    private final AuthProperties properties;

    public RegistrationService(UserRepository userRepository, PasswordEncoder passwordEncoder, PasswordPolicy passwordPolicy,
                               OtpService otpService, NicHasher nicHasher, EmailService emailService, AuthProperties properties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.otpService = otpService;
        this.nicHasher = nicHasher;
        this.emailService = emailService;
        this.properties = properties;
    }

    public User create(NewAccount in) {
        String email = in.email == null ? null : in.email.trim().toLowerCase(Locale.ROOT);
        String phone = PhoneNumbers.normalize(in.phoneNumber);

        // 1. input that is wrong regardless of what exists in the database
        if (in.role == null) {
            throw AuthException.invalidField("role", AuthCodes.ROLE_REQUIRED);
        }
        if (in.role == Role.ADMIN && !in.allowAdmin) {
            // Privileged accounts are provisioned by invitation, never through public sign-up.
            throw AuthException.invalidField("role", AuthCodes.ROLE_NOT_ALLOWED);
        }
        if (phone == null || !PhoneNumbers.isMobile(phone)) {
            throw AuthException.invalidField("phoneNumber", AuthCodes.PHONE_INVALID);
        }
        if (in.password != null) {
            passwordPolicy.enforce("password", in.password, email, phone);
        }
        String nic = NicHasher.normalize(in.nic);
        if (nic != null && !NicValidator.isValid(nic)) {
            throw AuthException.invalidField("nicNumber", AuthCodes.NIC_INVALID);
        }

        // 2. phone must be proven BEFORE anything is revealed about existing accounts
        if (!otpService.isPhoneVerified(phone)) {
            throw AuthException.invalidField("phoneNumber", AuthCodes.PHONE_NOT_VERIFIED);
        }

        // 3. duplicates: one neutral answer for "email taken" and "phone taken"; the email owner is told by mail
        Optional<User> sameEmail = userRepository.findByEmailIgnoreCase(email);
        if (sameEmail.isPresent()) {
            emailService.sendDuplicateRegistrationNotice(sameEmail.get());
            throw AuthException.conflict(AuthCodes.ACCOUNT_EXISTS);
        }
        if (userRepository.findFirstByPhoneNumberIn(PhoneNumbers.variants(phone)).isPresent()) {
            throw AuthException.conflict(AuthCodes.ACCOUNT_EXISTS);
        }
        String nicHash = nicHasher.hash(nic);
        if (nicHash != null && userRepository.existsByNicHash(nicHash)) {
            throw AuthException.conflict(AuthCodes.NIC_IN_USE);
        }

        // 4. create
        User user = new User();
        user.setEmail(email);
        user.setPhoneNumber(phone);
        user.setFirstName(in.firstName == null ? null : in.firstName.trim());
        user.setLastName(in.lastName == null ? null : in.lastName.trim());
        user.setRole(in.role);
        user.setNicHash(nicHash);
        user.setAuthProvider(in.provider);
        user.setProviderId(in.providerId);
        user.setVerificationStatus(in.verificationStatus);
        user.setPreferredLanguage(AuthMessages.normalizeLanguage(in.preferredLanguage));
        if (in.consentGiven) {
            // The API layer rejects a missing acceptTerms (Bean Validation); here it is only recorded.
            user.setConsentVersion(properties.getConsentVersion());
            user.setConsentedAt(Instant.now());
        }
        if (in.emailVerified) {
            user.setEmailVerifiedAt(Instant.now());
        }
        if (in.password != null) {
            user.setPasswordHash(passwordEncoder.encode(in.password));
        }

        User saved;
        try {
            saved = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // Two sign-ups raced past the checks above and the unique constraint caught the loser.
            log.info("Concurrent duplicate registration rejected by a unique constraint");
            throw AuthException.conflict(AuthCodes.ACCOUNT_EXISTS);
        }

        otpService.consume(phone);
        return saved;
    }
}
