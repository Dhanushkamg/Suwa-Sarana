package com.suwasarana.api.admin;

import com.suwasarana.api.audit.AdminAuditService;
import com.suwasarana.api.audit.AuditAction;
import com.suwasarana.api.auth.AuthProperties;
import com.suwasarana.api.auth.token.AuthTokenRepository;
import com.suwasarana.api.auth.token.AuthTokenService;
import com.suwasarana.api.auth.token.AuthTokenType;
import com.suwasarana.api.email.EmailService;
import com.suwasarana.api.email.LoggingEmailSender;
import com.suwasarana.api.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * First-administrator bootstrap without SQL. When ADMIN_BOOTSTRAP_EMAIL is set and no usable administrator exists
 * (seeded demo accounts do not count), an invitation is created for that address:
 * production emails the link; any other environment prints it ONCE in the log (nobody reads mail in local development).
 * While an unused, unexpired bootstrap invitation exists nothing new is created, so restarts do not spam.
 * The link opens /accept-invite where the new administrator sets name, phone (OTP) and password.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AuthProperties properties;
    private final UserRepository userRepository;
    private final AuthTokenService tokenService;
    private final AuthTokenRepository tokenRepository;
    private final EmailService emailService;
    private final AdminAuditService audit;
    private final Environment environment;

    public AdminBootstrap(AuthProperties properties, UserRepository userRepository, AuthTokenService tokenService,
                          AuthTokenRepository tokenRepository, EmailService emailService, AdminAuditService audit,
                          Environment environment) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.tokenService = tokenService;
        this.tokenRepository = tokenRepository;
        this.emailService = emailService;
        this.audit = audit;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        String email = properties.getAdminBootstrapEmail() == null ? "" : properties.getAdminBootstrapEmail().trim().toLowerCase();
        if (email.isEmpty()) {
            return;
        }
        if (userRepository.countUsableAdmins() > 0) {
            log.info("ADMIN_BOOTSTRAP_EMAIL is set but an administrator already exists; nothing to do");
            return;
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            log.warn("ADMIN_BOOTSTRAP_EMAIL belongs to an existing non-admin account; promote it deliberately or use another address");
            return;
        }
        if (tokenRepository.existsByEmailAndTypeAndUsedAtIsNullAndExpiresAtAfter(email, AuthTokenType.ADMIN_INVITE, Instant.now())) {
            log.info("A bootstrap invitation for {} is still open; not creating another", LoggingEmailSender.maskEmail(email));
            return;
        }

        String token = tokenService.issueAdminInvite(email, null);
        audit.record(null, null, AuditAction.ADMIN_BOOTSTRAP_INVITED, null, null,
                "bootstrap invitation for " + LoggingEmailSender.maskEmail(email), "startup");

        if (environment.acceptsProfiles(Profiles.of("prod", "production"))) {
            emailService.sendAdminInvite(email, null, "en", token);
            log.warn("No administrator exists: an invitation was emailed to {}", LoggingEmailSender.maskEmail(email));
        } else {
            log.warn("""

                    ============================================================================
                     No administrator exists. Open this link ONCE to create the first one:
                     {}
                     (printed only in non-production environments; valid for {} hours)
                    ============================================================================""",
                    emailService.adminInviteLink(email, token), properties.getAdminInviteTtlHours());
        }
    }
}
