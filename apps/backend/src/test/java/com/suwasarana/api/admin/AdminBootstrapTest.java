package com.suwasarana.api.admin;

import com.suwasarana.api.audit.AdminAuditService;
import com.suwasarana.api.auth.AuthProperties;
import com.suwasarana.api.auth.token.AuthTokenRepository;
import com.suwasarana.api.auth.token.AuthTokenService;
import com.suwasarana.api.email.EmailService;
import com.suwasarana.api.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.env.MockEnvironment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminBootstrapTest {

    @Mock private UserRepository userRepository;
    @Mock private AuthTokenService tokenService;
    @Mock private AuthTokenRepository tokenRepository;
    @Mock private EmailService emailService;
    @Mock private AdminAuditService audit;

    private AuthProperties properties;
    private MockEnvironment environment;

    @BeforeEach
    void setUp() {
        properties = new AuthProperties();
        properties.setAdminBootstrapEmail("First.Admin@Example.com");
        environment = new MockEnvironment();
        when(tokenService.issueAdminInvite(anyString(), any())).thenReturn("raw-token");
        when(emailService.adminInviteLink(anyString(), anyString())).thenReturn("http://localhost:3000/accept-invite?token=raw-token");
    }

    private AdminBootstrap bootstrap() {
        return new AdminBootstrap(properties, userRepository, tokenService, tokenRepository, emailService, audit, environment);
    }

    @Test
    void notConfigured_doesNothing() {
        properties.setAdminBootstrapEmail("");

        bootstrap().run(null);

        verifyNoInteractions(tokenService, emailService);
    }

    @Test
    void anAdministratorAlreadyExists_doesNothing() {
        when(userRepository.countUsableAdmins()).thenReturn(1L);

        bootstrap().run(null);

        verify(tokenService, never()).issueAdminInvite(anyString(), any());
    }

    @Test
    void production_emailsTheInvitation_andNeverPrintsIt() {
        environment.setActiveProfiles("prod");

        bootstrap().run(null);

        verify(tokenService).issueAdminInvite("first.admin@example.com", null);
        verify(emailService).sendAdminInvite("first.admin@example.com", null, "en", "raw-token");
        verify(emailService, never()).adminInviteLink(anyString(), anyString());
    }

    @Test
    void outsideProduction_printsTheLinkOnce_insteadOfEmailing() {
        bootstrap().run(null);

        verify(emailService).adminInviteLink("first.admin@example.com", "raw-token");
        verify(emailService, never()).sendAdminInvite(anyString(), any(), anyString(), anyString());
    }

    @Test
    void anOpenInvitationIsNotDuplicatedOnRestart() {
        when(tokenRepository.existsByEmailAndTypeAndUsedAtIsNullAndExpiresAtAfter(anyString(), any(), any())).thenReturn(true);

        bootstrap().run(null);

        verify(tokenService, never()).issueAdminInvite(anyString(), any());
    }

    @Test
    void anExistingNonAdminAccountWithThatEmail_isNeverSilentlyPromoted() {
        when(userRepository.existsByEmailIgnoreCase("first.admin@example.com")).thenReturn(true);

        bootstrap().run(null);

        verify(tokenService, never()).issueAdminInvite(anyString(), any());
    }
}
