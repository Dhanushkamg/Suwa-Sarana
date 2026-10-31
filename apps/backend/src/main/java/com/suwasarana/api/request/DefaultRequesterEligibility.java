package com.suwasarana.api.request;

import com.suwasarana.api.exception.RequesterNotVerifiedException;
import com.suwasarana.api.security.VerificationGuard;
import com.suwasarana.api.user.Role;
import com.suwasarana.api.user.User;
import com.suwasarana.api.user.VerificationStatus;
import org.springframework.stereotype.Component;

/**
 * Request / stock rules on top of {@link VerificationGuard}:
 * <ul>
 *   <li>SUSPENDED and REJECTED accounts cannot raise requests at all ({@code auth.account.suspended},
 *       {@code verification.rejected});</li>
 *   <li>URGENT and CRITICAL requests alert donors fastest and widest: a hospital / blood bank must be VERIFIED with a
 *       confirmed email ({@code verification.required}, {@code email.verificationRequired});</li>
 *   <li>CRITICAL requests additionally need a VERIFIED account for every non-admin role
 *       ({@code request.requester_not_verified} for a plain requester);</li>
 *   <li>stock can be published only by VERIFIED institutions with a confirmed email.</li>
 * </ul>
 */
@Component
public class DefaultRequesterEligibility implements RequesterEligibility {

    private final VerificationGuard guard;

    public DefaultRequesterEligibility(VerificationGuard guard) {
        this.guard = guard;
    }

    @Override
    public void assertMayCreateRequest(User requester, Urgency urgency) {
        guard.requireNotBlocked(requester);
        if (urgency != Urgency.ROUTINE) {
            guard.requireVerified(requester);
        }
        if (urgency == Urgency.CRITICAL && requester.getRole() != Role.ADMIN
                && requester.getVerificationStatus() != VerificationStatus.VERIFIED) {
            throw new RequesterNotVerifiedException("Unverified users cannot create CRITICAL requests.");
        }
    }

    @Override
    public void assertVerifiedInstitution(User institution) {
        guard.requireVerified(institution);
    }
}
