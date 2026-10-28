package com.suwasarana.api.auth;

import com.suwasarana.api.auth.otp.OtpService;
import com.suwasarana.api.email.EmailService;
import com.suwasarana.api.security.HmacHasher;
import com.suwasarana.api.user.NicHasher;
import com.suwasarana.api.user.Role;
import com.suwasarana.api.user.User;
import com.suwasarana.api.user.UserRepository;
import com.suwasarana.api.user.VerificationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RegistrationServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private OtpService otpService;
    @Mock private EmailService emailService;

    private RegistrationService service;
    private NicHasher nicHasher;

    @BeforeEach
    void setUp() {
        AuthProperties properties = new AuthProperties();
        nicHasher = new NicHasher(new HmacHasher(properties));
        service = new RegistrationService(userRepository, passwordEncoder, new PasswordPolicy(), otpService, nicHasher, emailService, properties);
        when(otpService.isPhoneVerified(anyString())).thenReturn(true);
        when(passwordEncoder.encode(anyString())).thenReturn("encoded");
        when(userRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(userRepository.findFirstByPhoneNumberIn(anyCollection())).thenReturn(Optional.empty());
        when(userRepository.saveAndFlush(any())).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(99L);
            return u;
        });
    }

    private RegistrationService.NewAccount account() {
        RegistrationService.NewAccount a = new RegistrationService.NewAccount();
        a.email = "Nimal.Perera@Example.com ";
        a.phoneNumber = "0771234567";
        a.firstName = " Nimal ";
        a.lastName = "Perera";
        a.role = Role.DONOR;
        a.password = "a-long-unique-passphrase";
        a.consentGiven = true;
        return a;
    }

    private AuthException thrown(RegistrationService.NewAccount a) {
        return assertThrows(AuthException.class, () -> service.create(a));
    }

    @Test
    void createsTheAccount_normalised_withConsentRecorded() {
        RegistrationService.NewAccount a = account();
        a.preferredLanguage = "si";

        User user = service.create(a);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(saved.capture());
        User u = saved.getValue();
        assertEquals("nimal.perera@example.com", u.getEmail());
        assertEquals("+94771234567", u.getPhoneNumber());
        assertEquals("Nimal", u.getFirstName());
        assertEquals("encoded", u.getPasswordHash());
        assertEquals("si", u.getPreferredLanguage());
        assertEquals(VerificationStatus.PENDING, u.getVerificationStatus());
        assertEquals("2026-01", u.getConsentVersion());
        assertNotNull(u.getConsentedAt());
        assertFalse(u.isEmailVerified());
        assertEquals(99L, user.getId());
        verify(otpService).consume("+94771234567");
    }

    @Test
    void institutionalRoles_startPending_untilAnAdministratorVerifiesThem() {
        for (Role role : new Role[]{Role.HOSPITAL_REQUESTER, Role.BLOOD_BANK_REQUESTER}) {
            RegistrationService.NewAccount a = account();
            a.role = role;
            assertEquals(VerificationStatus.PENDING, service.create(a).getVerificationStatus());
        }
    }

    @Test
    void adminRole_isRefusedOnPublicSignUp_andNothingIsSaved() {
        RegistrationService.NewAccount a = account();
        a.role = Role.ADMIN;

        AuthException ex = thrown(a);

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals(AuthCodes.ROLE_NOT_ALLOWED, ex.getFieldErrors().get("role"));
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void invitationPath_mayCreateAnAdmin() {
        RegistrationService.NewAccount a = account();
        a.role = Role.ADMIN;
        a.allowAdmin = true;

        assertEquals(Role.ADMIN, service.create(a).getRole());
    }

    @Test
    void weakPassword_isAFieldError() {
        RegistrationService.NewAccount a = account();
        a.password = "password1234";

        assertEquals(AuthCodes.PASSWORD_COMMON, thrown(a).getFieldErrors().get("password"));
    }

    @Test
    void passwordEqualToTheEmail_isRefused() {
        RegistrationService.NewAccount a = account();
        a.email = "long.address.123@example.com";
        a.password = "long.address.123@example.com";

        assertEquals(AuthCodes.PASSWORD_SAME_AS_IDENTIFIER, thrown(a).getFieldErrors().get("password"));
    }

    @Test
    void unverifiedPhone_isRefused_andNothingAboutExistingAccountsLeaks() {
        when(otpService.isPhoneVerified(anyString())).thenReturn(false);
        User existing = new User();
        when(userRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.of(existing));

        AuthException ex = thrown(account());

        assertEquals(AuthCodes.PHONE_NOT_VERIFIED, ex.getFieldErrors().get("phoneNumber"));
        verifyNoInteractions(emailService);
    }

    @Test
    void duplicateEmail_and_duplicatePhone_giveTheSameNeutral409() {
        User existing = new User();
        existing.setEmail("nimal.perera@example.com");
        when(userRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.of(existing));
        AuthException emailDup = thrown(account());

        when(userRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(userRepository.findFirstByPhoneNumberIn(anyCollection())).thenReturn(Optional.of(existing));
        AuthException phoneDup = thrown(account());

        assertEquals(HttpStatus.CONFLICT, emailDup.getStatus());
        assertEquals(AuthCodes.ACCOUNT_EXISTS, emailDup.getCode());
        assertEquals(emailDup.getCode(), phoneDup.getCode(), "the response does not say WHICH of the two exists");
        assertEquals(emailDup.getFieldErrors(), phoneDup.getFieldErrors());
    }

    @Test
    void duplicateEmail_notifiesTheExistingOwnerByEmail() {
        User existing = new User();
        when(userRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.of(existing));

        thrown(account());

        verify(emailService).sendDuplicateRegistrationNotice(existing);
    }

    @Test
    void concurrentDuplicateSignUp_isA409_notA500() {
        doThrow(new DataIntegrityViolationException("uk_users_email")).when(userRepository).saveAndFlush(any());

        AuthException ex = thrown(account());

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals(AuthCodes.ACCOUNT_EXISTS, ex.getCode());
        verify(otpService, never()).consume(anyString());
    }

    @Test
    void blankNic_becomesNull_notAnEmptyStringThatCollidesOnTheSecondUser() {
        RegistrationService.NewAccount a = account();
        a.nic = "   ";

        service.create(a);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(saved.capture());
        assertNull(saved.getValue().getNicHash());
        assertNull(saved.getValue().getNicNumber());
        verify(userRepository, never()).existsByNicHash(any());
    }

    @Test
    void nic_isStoredOnlyAsAKeyedHash_andAnInvalidOneIsRefused() {
        RegistrationService.NewAccount a = account();
        a.nic = "199012345678";
        service.create(a);
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(saved.capture());
        assertEquals(64, saved.getValue().getNicHash().length());
        assertNull(saved.getValue().getNicNumber(), "no clear-text NIC");
        assertEquals(nicHasher.hash("199012345678V".substring(0, 12)), saved.getValue().getNicHash());
        assertEquals(nicHasher.hash(" 199012345678 "), saved.getValue().getNicHash(), "normalised before hashing");

        RegistrationService.NewAccount bad = account();
        bad.nic = "12345";
        assertEquals(AuthCodes.NIC_INVALID, thrown(bad).getFieldErrors().get("nicNumber"));
    }

    @Test
    void duplicateNic_is409() {
        RegistrationService.NewAccount a = account();
        a.nic = "199012345678";
        when(userRepository.existsByNicHash(nicHasher.hash("199012345678"))).thenReturn(true);

        AuthException ex = thrown(a);

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals(AuthCodes.NIC_IN_USE, ex.getCode());
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void googleAccounts_haveNoPassword_andAVerifiedEmail() {
        RegistrationService.NewAccount a = account();
        a.password = null;
        a.provider = com.suwasarana.api.user.AuthProvider.GOOGLE;
        a.providerId = "google-sub-1";
        a.emailVerified = true;

        User u = service.create(a);

        assertNull(u.getPasswordHash());
        assertTrue(u.isEmailVerified());
        assertEquals("google-sub-1", u.getProviderId());
    }
}
