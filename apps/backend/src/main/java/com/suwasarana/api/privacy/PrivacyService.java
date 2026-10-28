package com.suwasarana.api.privacy;

import com.suwasarana.api.audit.AdminAuditService;
import com.suwasarana.api.audit.AuditAction;
import com.suwasarana.api.auth.AuthCodes;
import com.suwasarana.api.auth.AuthException;
import com.suwasarana.api.auth.LoginAttemptService;
import com.suwasarana.api.auth.SessionService;
import com.suwasarana.api.auth.otp.OtpService;
import com.suwasarana.api.auth.token.AuthTokenRepository;
import com.suwasarana.api.email.EmailService;
import com.suwasarana.api.user.Role;
import com.suwasarana.api.user.User;
import com.suwasarana.api.user.UserRepository;
import com.suwasarana.api.user.UserStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Data-subject rights (Sri Lanka PDPA): a complete export of what is held about a person, and erasure.
 *
 * <b>Export</b> reads every table that stores something about the user straight from the database (generic column
 * maps), so a column added later by another module is exported without code changes here. Secrets (password hash, token
 * hashes) are never included.
 *
 * <b>Erasure</b> is anonymisation: the account row is kept (other tables reference it) but every personal field is
 * overwritten, the login is impossible, sessions and tokens are gone, active requests / bookings / camps are cancelled, and
 * donor details (date of birth, exact location, weight, availability) are scrubbed. Aggregate history (a donation happened,
 * a request existed) stays without anything that identifies a person. The audit trail is kept (legal accountability).
 */
@Service
public class PrivacyService {

    private static final Logger log = LoggerFactory.getLogger(PrivacyService.class);
    private static final LocalDate SCRUBBED_DATE_OF_BIRTH = LocalDate.of(1900, 1, 1);

    private final JdbcTemplate jdbc;
    private final UserRepository userRepository;
    private final SessionService sessionService;
    private final OtpService otpService;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptService loginAttempts;
    private final AuthTokenRepository authTokenRepository;
    private final AdminAuditService adminAudit;
    private final EmailService emailService;

    public PrivacyService(JdbcTemplate jdbc, UserRepository userRepository, SessionService sessionService,
                          OtpService otpService, PasswordEncoder passwordEncoder, LoginAttemptService loginAttempts,
                          AuthTokenRepository authTokenRepository, AdminAuditService adminAudit, EmailService emailService) {
        this.jdbc = jdbc;
        this.userRepository = userRepository;
        this.sessionService = sessionService;
        this.otpService = otpService;
        this.passwordEncoder = passwordEncoder;
        this.loginAttempts = loginAttempts;
        this.authTokenRepository = authTokenRepository;
        this.adminAudit = adminAudit;
        this.emailService = emailService;
    }

    // ---- export ----------------------------------------------------------------------------------------------

    /** Everything held about the user, as a JSON-friendly map. The caller records the audit entry. */
    @Transactional(readOnly = true)
    public Map<String, Object> export(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> AuthException.notFound(AuthCodes.NOT_FOUND));

        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("generatedAt", Instant.now().toString());
        doc.put("format", "suwa-sarana-personal-data-export/1");

        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("id", user.getId());
        profile.put("email", user.getEmail());
        profile.put("emailVerifiedAt", str(user.getEmailVerifiedAt()));
        profile.put("phoneNumber", user.getPhoneNumber());
        profile.put("firstName", user.getFirstName());
        profile.put("lastName", user.getLastName());
        profile.put("role", user.getRole().name());
        profile.put("verificationStatus", user.getVerificationStatus().name());
        profile.put("verificationReason", user.getVerificationReason());
        profile.put("accountStatus", user.getStatus().name());
        profile.put("signInMethod", user.getAuthProvider().name());
        profile.put("hasPassword", user.hasUsablePassword());
        profile.put("googleLinked", user.getProviderId() != null);
        profile.put("preferredLanguage", user.getPreferredLanguage());
        profile.put("nationalIdProvided", user.getNicHash() != null);
        profile.put("nationalIdNote", "The NIC is not stored; only a keyed hash used to detect duplicate registrations is kept.");
        profile.put("registeredAt", str(user.getCreatedAt()));
        doc.put("profile", profile);

        Map<String, Object> consent = new LinkedHashMap<>();
        consent.put("version", user.getConsentVersion());
        consent.put("acceptedAt", str(user.getConsentedAt()));
        doc.put("consent", consent);

        List<Map<String, Object>> donorProfiles = rows("select * from donor_profiles where user_id = ?", userId);
        doc.put("donorProfile", donorProfiles.isEmpty() ? null : donorProfiles.get(0));
        Long donorId = donorProfiles.isEmpty() ? null : ((Number) donorProfiles.get(0).get("id")).longValue();

        if (donorId != null) {
            doc.put("donorDeferrals", rows("select * from donor_deferrals where donor_id = ?", donorId));
            doc.put("matches", rows("select * from request_matches where donor_id = ?", donorId));
            doc.put("slotBookings", rows("select * from slot_bookings where donor_id = ?", donorId));
            doc.put("campRegistrations", rows("select * from camp_registrations where donor_id = ?", donorId));
            doc.put("notificationLogs", rows("select * from notification_logs where donor_id = ?", donorId));
        } else {
            for (String section : List.of("donorDeferrals", "matches", "slotBookings", "campRegistrations", "notificationLogs")) {
                doc.put(section, List.of());
            }
        }
        doc.put("bloodRequests", rows("select * from blood_requests where requester_id = ?", userId));
        doc.put("requestReports", rows("select * from request_reports where reported_by = ?", userId));
        doc.put("campsOrganised", rows("select * from donation_camps where organizer_id = ?", userId));
        doc.put("slotsHosted", rows("select * from donation_slots where hospital_id = ?", userId));
        doc.put("contactMessages", rows("select * from contact_messages where lower(email) = lower(?)", user.getEmail()));
        doc.put("administrativeActionsAboutYou", rows(
                "select action, reason, details, created_at from admin_audit_log where target_user_id = ? order by created_at", userId));
        doc.put("activeSessions", jdbc.queryForObject("select count(*) from refresh_tokens where user_id = ? and used_at is null and expires_at > ?",
                Long.class, userId, Timestamp.from(Instant.now())));
        return doc;
    }

    // ---- erasure ---------------------------------------------------------------------------------------------

    /**
     * Anonymises the account after the owner proved who they are with the password or a fresh SMS code.
     *
     * @param password the account password (accounts that have one), or null
     * @param otpCode  a code sent with {@code POST /api/users/me/send-otp}, or null
     */
    @Transactional(noRollbackFor = AuthException.class) // a wrong confirmation must still count towards the lockout
    public void deleteAccount(Long userId, String password, String otpCode, String ipAddress) {
        User user = userRepository.findById(userId).orElseThrow(() -> AuthException.unauthorized(AuthCodes.NOT_SIGNED_IN));
        if (user.getRole() == Role.ADMIN) {
            // Removing an administrator is a deliberate, audited operation, not a self-service button.
            throw AuthException.forbidden(AuthCodes.DELETE_ADMIN_FORBIDDEN);
        }
        confirmIdentity(user, password, otpCode, ipAddress);

        String email = user.getEmail();
        String firstName = user.getFirstName();
        String language = user.getPreferredLanguage();
        String phone = user.getPhoneNumber();
        Timestamp now = Timestamp.from(Instant.now());

        List<Long> donorIds = jdbc.queryForList("select id from donor_profiles where user_id = ?", Long.class, userId);
        for (Long donorId : donorIds) {
            // release seats held by future bookings, then cancel them
            jdbc.update("update donation_slots set booked_count = booked_count - 1, "
                    + "status = case when status = 'FULL' then 'OPEN' else status end "
                    + "where id in (select slot_id from slot_bookings where donor_id = ? and status = 'BOOKED')", donorId);
            jdbc.update("update slot_bookings set status = 'CANCELLED' where donor_id = ? and status = 'BOOKED'", donorId);
            jdbc.update("update request_matches set response = 'NO_RESPONSE' where donor_id = ? and (response is null or response = 'PENDING')", donorId);
            jdbc.update("update notification_logs set recipient = null, message_body = null where donor_id = ?", donorId);
            jdbc.update("update donor_deferrals set notes = null where donor_id = ?", donorId);
        }
        jdbc.update("update donor_profiles set date_of_birth = ?, weight_kg = null, latitude = 0, longitude = 0, "
                + "is_available = ?, quiet_hours_start = null, quiet_hours_end = null where user_id = ?",
                java.sql.Date.valueOf(SCRUBBED_DATE_OF_BIRTH), false, userId);

        jdbc.update("update blood_requests set status = 'CANCELLED' where requester_id = ? and status in ('OPEN', 'ESCALATING', 'MATCHED')", userId);
        jdbc.update("update request_reports set reason = '[removed]' where reported_by = ?", userId);
        jdbc.update("update donation_camps set status = 'CANCELLED' where organizer_id = ? and status = 'SCHEDULED'", userId);
        jdbc.update("update donation_slots set status = 'CANCELLED' where hospital_id = ? and status in ('OPEN', 'FULL')", userId);
        jdbc.update("delete from contact_messages where lower(email) = lower(?)", email);

        jdbc.update("delete from refresh_tokens where user_id = ?", userId);
        authTokenRepository.deleteByUserId(userId);
        otpService.consume(phone);

        jdbc.update("update users set email = ?, phone_number = ?, first_name = 'Deleted', last_name = 'User', password_hash = null, "
                        + "provider_id = null, nic_hash = null, nic_number = null, email_verified_at = null, status = ?, "
                        + "status_changed_at = ?, deleted_at = ?, token_version = token_version + 1, failed_login_count = 0, "
                        + "locked_until = null, updated_at = ? where id = ?",
                "deleted-" + userId + "@deleted.invalid", "deleted-" + userId, UserStatus.DELETED.name(), now, now, now, userId);

        adminAudit.record(userId, user.getRole(), AuditAction.ACCOUNT_DELETED, userId, null, "account erased at the owner's request", ipAddress);
        log.info("Account {} erased at the owner's request", userId);

        emailService.sendAccountDeleted(email, firstName, language);
    }

    private void confirmIdentity(User user, String password, String otpCode, String ipAddress) {
        boolean hasPassword = password != null && !password.isEmpty();
        boolean hasOtp = otpCode != null && !otpCode.isBlank();
        if (!hasPassword && !hasOtp) {
            throw AuthException.badRequest(AuthCodes.DELETE_CONFIRMATION_REQUIRED);
        }
        if (hasPassword) {
            loginAttempts.assertNotLocked(user, ipAddress);
            if (user.hasUsablePassword() && passwordEncoder.matches(password, user.getPasswordHash())) {
                return;
            }
            loginAttempts.recordFailure(user);
            throw AuthException.of(org.springframework.http.HttpStatus.FORBIDDEN, AuthCodes.DELETE_CONFIRMATION_INVALID);
        }
        if (!otpService.verifyOtp(user.getPhoneNumber(), otpCode)) {
            throw AuthException.of(org.springframework.http.HttpStatus.FORBIDDEN, AuthCodes.DELETE_CONFIRMATION_INVALID);
        }
    }

    // ---- helpers ---------------------------------------------------------------------------------------------

    private List<Map<String, Object>> rows(String sql, Object... args) {
        try {
            List<Map<String, Object>> raw = jdbc.queryForList(sql, args);
            List<Map<String, Object>> out = new ArrayList<>(raw.size());
            for (Map<String, Object> row : raw) {
                Map<String, Object> clean = new LinkedHashMap<>();
                for (Map.Entry<String, Object> e : row.entrySet()) {
                    clean.put(e.getKey().toLowerCase(), jsonValue(e.getValue()));
                }
                out.add(clean);
            }
            return out;
        } catch (DataAccessException e) {
            // Never silently drop a section of a data-subject export: say so, and log for the operator.
            log.error("Personal-data export: query failed ({})", e.getClass().getSimpleName());
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("error", "this section could not be read; contact support");
            return List.of(err);
        }
    }

    private static Object jsonValue(Object v) {
        if (v == null || v instanceof Number || v instanceof Boolean || v instanceof String) {
            return v instanceof BigDecimal bd ? bd.toPlainString() : v;
        }
        if (v instanceof Timestamp ts) {
            return ts.toInstant().toString();
        }
        if (v instanceof java.sql.Date || v instanceof java.sql.Time || v instanceof java.time.temporal.TemporalAccessor
                || v instanceof java.time.OffsetDateTime) {
            return v.toString();
        }
        if (v instanceof byte[]) {
            return "[binary]";
        }
        return v.toString();
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }
}
