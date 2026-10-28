package com.suwasarana.api.security;

import com.suwasarana.api.auth.AuthCodes;
import com.suwasarana.api.auth.AuthException;
import com.suwasarana.api.user.Role;
import com.suwasarana.api.user.User;
import com.suwasarana.api.user.UserRepository;
import com.suwasarana.api.user.UserStatus;
import com.suwasarana.api.user.VerificationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VerificationGuardTest {

    @Mock private UserRepository userRepository;
    private VerificationGuard guard;

    @BeforeEach
    void setUp() {
        guard = new VerificationGuard(userRepository);
    }

    private User user(Role role, VerificationStatus verification, boolean emailVerified) {
        User u = new User();
        u.setId(5L);
        u.setRole(role);
        u.setVerificationStatus(verification);
        u.setEmailVerifiedAt(emailVerified ? Instant.now() : null);
        return u;
    }

    private void assertForbidden(String code, org.junit.jupiter.api.function.Executable e) {
        AuthException ex = assertThrows(AuthException.class, e);
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertEquals(code, ex.getCode());
    }

    @Test
    void pendingHospitalAndBloodBank_cannotPublish() {
        for (Role role : new Role[]{Role.HOSPITAL_REQUESTER, Role.BLOOD_BANK_REQUESTER}) {
            User u = user(role, VerificationStatus.PENDING, true);
            assertForbidden(AuthCodes.VERIFICATION_REQUIRED, () -> guard.requireVerified(u));
        }
    }

    @Test
    void verifiedInstitution_withAConfirmedEmail_mayPublish() {
        assertDoesNotThrow(() -> guard.requireVerified(user(Role.HOSPITAL_REQUESTER, VerificationStatus.VERIFIED, true)));
        assertDoesNotThrow(() -> guard.requireVerified(user(Role.BLOOD_BANK_REQUESTER, VerificationStatus.VERIFIED, true)));
    }

    @Test
    void verifiedInstitution_withAnUnconfirmedEmail_isAskedToConfirmIt() {
        User u = user(Role.HOSPITAL_REQUESTER, VerificationStatus.VERIFIED, false);

        assertForbidden(AuthCodes.EMAIL_NOT_VERIFIED, () -> guard.requireVerified(u));
    }

    @Test
    void rejectedUsers_areBlockedEverywhere() {
        for (Role role : Role.values()) {
            User u = user(role, VerificationStatus.REJECTED, true);
            assertForbidden(AuthCodes.VERIFICATION_REJECTED, () -> guard.requireNotBlocked(u));
            assertForbidden(AuthCodes.VERIFICATION_REJECTED, () -> guard.requireVerified(u));
        }
    }

    @Test
    void suspendedAndErasedUsers_areBlockedEverywhere() {
        for (UserStatus status : new UserStatus[]{UserStatus.SUSPENDED, UserStatus.DELETED}) {
            for (Role role : Role.values()) {
                User u = user(role, VerificationStatus.VERIFIED, true);
                u.setStatus(status);
                assertForbidden(AuthCodes.ACCOUNT_SUSPENDED, () -> guard.requireNotBlocked(u));
                assertForbidden(AuthCodes.ACCOUNT_SUSPENDED, () -> guard.requireVerified(u));
            }
        }
    }

    @Test
    void donorsAndPlainRequesters_arenotHeldToInstitutionalVerification() {
        assertDoesNotThrow(() -> guard.requireVerified(user(Role.DONOR, VerificationStatus.PENDING, false)));
        assertDoesNotThrow(() -> guard.requireVerified(user(Role.REQUESTER, VerificationStatus.PENDING, false)));
        assertDoesNotThrow(() -> guard.requireNotBlocked(user(Role.DONOR, VerificationStatus.PENDING, false)));
    }

    @Test
    void strictVariant_alsoHoldsPlainRequestersToVerification() {
        assertForbidden(AuthCodes.VERIFICATION_REQUIRED, () -> guard.requireVerifiedStrict(user(Role.REQUESTER, VerificationStatus.PENDING, true)));
        assertDoesNotThrow(() -> guard.requireVerifiedStrict(user(Role.REQUESTER, VerificationStatus.VERIFIED, true)));
        assertDoesNotThrow(() -> guard.requireVerifiedStrict(user(Role.ADMIN, VerificationStatus.PENDING, true)));
    }

    @Test
    void admins_passVerification_butNotWhenSuspended() {
        User admin = user(Role.ADMIN, VerificationStatus.PENDING, false);
        assertDoesNotThrow(() -> guard.requireVerified(admin));
        admin.setStatus(UserStatus.SUSPENDED);
        assertForbidden(AuthCodes.ACCOUNT_SUSPENDED, () -> guard.requireVerified(admin));
    }

    @Test
    void idOverloads_loadTheUser_andRejectUnknownIds() {
        when(userRepository.findById(5L)).thenReturn(Optional.of(user(Role.HOSPITAL_REQUESTER, VerificationStatus.PENDING, true)));
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        assertForbidden(AuthCodes.VERIFICATION_REQUIRED, () -> guard.requireVerified(5L));
        assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(AuthException.class, () -> guard.requireNotBlocked(404L)).getStatus());
        assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(AuthException.class, () -> guard.requireNotBlocked((Long) null)).getStatus());
    }
}
