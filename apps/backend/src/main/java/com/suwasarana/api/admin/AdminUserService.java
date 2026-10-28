package com.suwasarana.api.admin;

import com.suwasarana.api.audit.AdminAuditService;
import com.suwasarana.api.audit.AuditAction;
import com.suwasarana.api.auth.AuthCodes;
import com.suwasarana.api.auth.AuthException;
import com.suwasarana.api.auth.AuthProperties;
import com.suwasarana.api.auth.SessionService;
import com.suwasarana.api.auth.token.AuthTokenService;
import com.suwasarana.api.email.AccountNotifier;
import com.suwasarana.api.email.EmailService;
import com.suwasarana.api.email.LoggingEmailSender;
import com.suwasarana.api.privacy.PrivacyService;
import com.suwasarana.api.user.Role;
import com.suwasarana.api.user.User;
import com.suwasarana.api.user.UserRepository;
import com.suwasarana.api.user.UserStatus;
import com.suwasarana.api.user.VerificationStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Administrative workflow on accounts: verification decisions, suspension, role changes, invitations, exports.
 * Every mutation (1) needs a reason, (2) refuses to touch an ADMIN account, (3) writes a persisted audit row in the same
 * transaction and (4) tells the user (email + SMS) after the decision is stored.
 */
@Service
public class AdminUserService {

    /** Roles an administrator verifies by default: institutions. Donors never need verification. */
    public static final Set<Role> DEFAULT_QUEUE_ROLES = EnumSet.of(Role.HOSPITAL_REQUESTER, Role.BLOOD_BANK_REQUESTER);
    /** Roles whose verification status an administrator may decide. */
    public static final Set<Role> VERIFIABLE_ROLES = EnumSet.of(Role.HOSPITAL_REQUESTER, Role.BLOOD_BANK_REQUESTER, Role.REQUESTER);

    private final UserRepository userRepository;
    private final SessionService sessionService;
    private final AdminAuditService audit;
    private final AccountNotifier notifier;
    private final AuthTokenService tokenService;
    private final EmailService emailService;
    private final PrivacyService privacyService;
    private final AuthProperties properties;

    public AdminUserService(UserRepository userRepository, SessionService sessionService, AdminAuditService audit,
                            AccountNotifier notifier, AuthTokenService tokenService, EmailService emailService,
                            PrivacyService privacyService, AuthProperties properties) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.sessionService = sessionService;
        this.audit = audit;
        this.notifier = notifier;
        this.tokenService = tokenService;
        this.emailService = emailService;
        this.privacyService = privacyService;
    }

    // ---- verification queue ----------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<User> verificationQueue(VerificationStatus status, Role role, int page, int size) {
        Set<Role> roles = role == null ? DEFAULT_QUEUE_ROLES : EnumSet.of(role);
        if (role == Role.ADMIN || role == Role.DONOR) {
            throw AuthException.badRequest(AuthCodes.ADMIN_NOT_VERIFIABLE);
        }
        return userRepository.findByVerificationStatusAndRoleInAndStatus(
                status == null ? VerificationStatus.PENDING : status, roles, UserStatus.ACTIVE,
                pageable(page, size, Sort.by("createdAt").ascending()));
    }

    /** Approve (VERIFIED) or reject (REJECTED) an account; a reason is mandatory and is sent to the user. */
    @Transactional
    public User decideVerification(Long actorId, Long targetId, boolean approve, String reason, String ip) {
        requireReason(reason);
        User target = loadModifiable(actorId, targetId);
        if (!VERIFIABLE_ROLES.contains(target.getRole())) {
            throw AuthException.badRequest(AuthCodes.ADMIN_NOT_VERIFIABLE);
        }
        VerificationStatus next = approve ? VerificationStatus.VERIFIED : VerificationStatus.REJECTED;
        if (target.getVerificationStatus() == next) {
            throw AuthException.conflict(AuthCodes.ADMIN_ALREADY_DECIDED);
        }
        VerificationStatus previous = target.getVerificationStatus();
        target.setVerificationStatus(next);
        target.setVerificationReviewedBy(actorId);
        target.setVerificationReviewedAt(Instant.now());
        target.setVerificationReason(reason.trim());
        userRepository.save(target);

        audit.record(actorId, Role.ADMIN, approve ? AuditAction.VERIFICATION_APPROVED : AuditAction.VERIFICATION_REJECTED,
                targetId, reason, "verification " + previous + " -> " + next + " (" + target.getRole() + ")", ip);
        notifier.verificationDecision(target, approve, reason.trim());
        return target;
    }

    // ---- suspension ------------------------------------------------------------------------------------------

    @Transactional
    public User suspend(Long actorId, Long targetId, String reason, String ip) {
        requireReason(reason);
        User target = loadModifiable(actorId, targetId);
        if (target.getStatus() != UserStatus.ACTIVE) {
            throw AuthException.conflict(AuthCodes.ADMIN_STATUS_UNCHANGED);
        }
        target.setStatus(UserStatus.SUSPENDED);
        target.setStatusChangedAt(Instant.now());
        userRepository.save(target);
        sessionService.revokeAll(target); // sign the person out everywhere, now

        audit.record(actorId, Role.ADMIN, AuditAction.USER_SUSPENDED, targetId, reason, null, ip);
        notifier.suspended(target, reason.trim());
        return target;
    }

    @Transactional
    public User restore(Long actorId, Long targetId, String reason, String ip) {
        requireReason(reason);
        User target = loadModifiable(actorId, targetId);
        if (target.getStatus() != UserStatus.SUSPENDED) {
            throw AuthException.conflict(AuthCodes.ADMIN_STATUS_UNCHANGED);
        }
        target.setStatus(UserStatus.ACTIVE);
        target.setStatusChangedAt(Instant.now());
        userRepository.save(target);

        audit.record(actorId, Role.ADMIN, AuditAction.USER_RESTORED, targetId, reason, null, ip);
        notifier.restored(target);
        return target;
    }

    // ---- role change -----------------------------------------------------------------------------------------

    /** Changes a non-admin role. Admin rights are never granted here (use invitations). Institutional roles restart verification. */
    @Transactional
    public User changeRole(Long actorId, Long targetId, Role newRole, String reason, String ip) {
        requireReason(reason);
        if (newRole == null || newRole == Role.ADMIN) {
            throw AuthException.badRequest(AuthCodes.ADMIN_ROLE_INVALID);
        }
        User target = loadModifiable(actorId, targetId);
        Role previous = target.getRole();
        if (previous == newRole) {
            throw AuthException.conflict(AuthCodes.ADMIN_STATUS_UNCHANGED);
        }
        target.setRole(newRole);
        // A role change never carries over the approval given for another role: verification starts again.
        target.setVerificationStatus(VerificationStatus.PENDING);
        target.setVerificationReviewedBy(null);
        target.setVerificationReviewedAt(null);
        target.setVerificationReason(null);
        userRepository.save(target);
        sessionService.revokeAll(target); // the client caches the role: force a fresh sign-in

        audit.record(actorId, Role.ADMIN, AuditAction.ROLE_CHANGED, targetId, reason, previous + " -> " + newRole, ip);
        return target;
    }

    // ---- search / export -------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<User> search(String query, Role role, UserStatus status, int page, int size) {
        String q = query == null || query.isBlank() ? null : "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
        Specification<User> spec = (root, cq, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (role != null) {
                p.add(cb.equal(root.get("role"), role));
            }
            if (status != null) {
                p.add(cb.equal(root.get("status"), status));
            }
            if (q != null) {
                p.add(cb.or(
                        cb.like(cb.lower(root.get("email")), q),
                        cb.like(root.get("phoneNumber"), q),
                        cb.like(cb.lower(root.get("firstName")), q),
                        cb.like(cb.lower(root.get("lastName")), q)));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
        return userRepository.findAll(spec, pageable(page, size, Sort.by("id").descending()));
    }

    /** Full personal-data export of a user for a data-subject request; the reason and the export are audited. */
    @Transactional
    public Map<String, Object> exportUserData(Long actorId, Long targetId, String reason, String ip) {
        requireReason(reason);
        userRepository.findById(targetId).orElseThrow(() -> AuthException.notFound(AuthCodes.NOT_FOUND));
        audit.record(actorId, Role.ADMIN, AuditAction.DATA_EXPORT, targetId, reason, "exported by an administrator", ip);
        return privacyService.export(targetId);
    }

    // ---- invitations -----------------------------------------------------------------------------------------

    /** Creates a single-use invitation to become an administrator and emails the link. Returns when it expires. */
    @Transactional
    public Instant inviteAdmin(Long actorId, String email, String firstName, String language, String ip) {
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw AuthException.conflict(AuthCodes.INVITE_EXISTS);
        }
        String token = tokenService.issueAdminInvite(email, actorId);
        emailService.sendAdminInvite(email, firstName, language, token);
        audit.record(actorId, Role.ADMIN, AuditAction.ADMIN_INVITED, null, null,
                "invitation sent to " + LoggingEmailSender.maskEmail(email), ip);
        return Instant.now().plusSeconds(3600L * properties.getAdminInviteTtlHours());
    }

    // ---- helpers ---------------------------------------------------------------------------------------------

    /** The target must exist, must not be an administrator and must not be the actor. */
    private User loadModifiable(Long actorId, Long targetId) {
        User target = userRepository.findById(targetId).orElseThrow(() -> AuthException.notFound(AuthCodes.NOT_FOUND));
        if (target.getStatus() == UserStatus.DELETED) {
            throw AuthException.notFound(AuthCodes.NOT_FOUND);
        }
        if (target.getRole() == Role.ADMIN) {
            throw AuthException.forbidden(AuthCodes.ADMIN_TARGET_PROTECTED);
        }
        if (target.getId().equals(actorId)) {
            throw AuthException.forbidden(AuthCodes.ADMIN_TARGET_SELF);
        }
        return target;
    }

    private static void requireReason(String reason) {
        if (reason == null || reason.trim().length() < 3) {
            throw AuthException.invalidField("reason", AuthCodes.REASON_REQUIRED);
        }
        if (reason.trim().length() > 500) {
            throw AuthException.invalidField("reason", AuthCodes.REASON_TOO_LONG);
        }
    }

    private static Pageable pageable(int page, int size, Sort sort) {
        return PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)), sort);
    }
}
