package com.suwasarana.api.security;

import com.suwasarana.api.auth.AuthCodes;
import com.suwasarana.api.auth.AuthException;
import com.suwasarana.api.user.Role;
import com.suwasarana.api.user.User;
import com.suwasarana.api.user.UserRepository;
import com.suwasarana.api.user.UserStatus;
import com.suwasarana.api.user.VerificationStatus;
import org.springframework.stereotype.Service;

/**
 * Server-side gate for actions that reach many people. Other modules call it at the top of the service method (never rely
 * on the UI hiding a button):
 *
 * <pre>
 *   verificationGuard.requireNotBlocked(userId);   // any action by a signed-in user
 *   verificationGuard.requireVerified(userId);     // publish camps/slots, URGENT+ requests, check-ins
 *   verificationGuard.requireVerifiedStrict(userId); // when a plain REQUESTER must be admin-verified too (e.g. CRITICAL)
 * </pre>
 *
 * Failures are {@link AuthException}s with HTTP 403 and a code:
 * <ul>
 *   <li>{@code auth.account.suspended} - account suspended (or erased);</li>
 *   <li>{@code verification.rejected} - an administrator rejected the account's verification;</li>
 *   <li>{@code verification.required} - institutional account (hospital / blood bank) not yet verified by an administrator;</li>
 *   <li>{@code email.verificationRequired} - institutional account whose email address is still unconfirmed.</li>
 * </ul>
 * ADMIN always passes the verification checks (but is still blocked if suspended).
 */
@Service
public class VerificationGuard {

    private final UserRepository userRepository;

    public VerificationGuard(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // ---- blocked (suspended / erased / rejected) -------------------------------------------------------------

    public void requireNotBlocked(Long userId) {
        requireNotBlocked(load(userId));
    }

    public void requireNotBlocked(User user) {
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw AuthException.forbidden(AuthCodes.ACCOUNT_SUSPENDED);
        }
        if (user.getVerificationStatus() == VerificationStatus.REJECTED) {
            throw AuthException.forbidden(AuthCodes.VERIFICATION_REJECTED);
        }
    }

    // ---- institutional verification ---------------------------------------------------------------------------

    /** Hospitals and blood banks must be VERIFIED (and have a confirmed email) before publishing; other roles pass. */
    public void requireVerified(Long userId) {
        requireVerified(load(userId));
    }

    public void requireVerified(User user) {
        requireNotBlocked(user);
        if (user.getRole() == Role.ADMIN || !user.getRole().isInstitutional()) {
            return;
        }
        requireVerifiedStatus(user);
        if (!user.isEmailVerified()) {
            throw AuthException.forbidden(AuthCodes.EMAIL_NOT_VERIFIED);
        }
    }

    /** Like {@link #requireVerified(User)} but EVERY non-admin role (donor excluded by callers) must be VERIFIED. */
    public void requireVerifiedStrict(Long userId) {
        requireVerifiedStrict(load(userId));
    }

    public void requireVerifiedStrict(User user) {
        requireNotBlocked(user);
        if (user.getRole() == Role.ADMIN) {
            return;
        }
        requireVerifiedStatus(user);
    }

    /** The account's email address must be confirmed (for callers that want that on top, e.g. requests by a plain REQUESTER). */
    public void requireEmailVerified(Long userId) {
        User user = load(userId);
        requireNotBlocked(user);
        if (!user.isEmailVerified()) {
            throw AuthException.forbidden(AuthCodes.EMAIL_NOT_VERIFIED);
        }
    }

    private static void requireVerifiedStatus(User user) {
        if (user.getVerificationStatus() != VerificationStatus.VERIFIED) {
            throw AuthException.forbidden(AuthCodes.VERIFICATION_REQUIRED);
        }
    }

    private User load(Long userId) {
        if (userId == null) {
            throw AuthException.unauthorized(AuthCodes.NOT_SIGNED_IN);
        }
        return userRepository.findById(userId).orElseThrow(() -> AuthException.unauthorized(AuthCodes.NOT_SIGNED_IN));
    }
}
