package com.suwasarana.api.request;

import com.suwasarana.api.user.User;

/**
 * Who may raise requests and publish institution data. A deliberately tiny seam (so request / inventory services can be
 * unit-tested without a database); the production implementation {@link DefaultRequesterEligibility} delegates to the
 * shared {@code security.VerificationGuard}, the single place that knows about verification and blocked accounts.
 */
public interface RequesterEligibility {

    /** Throws when {@code requester} may not create a request of this urgency. */
    void assertMayCreateRequest(User requester, Urgency urgency);

    /** Throws unless {@code institution} is a VERIFIED hospital / blood bank (with confirmed email) allowed to publish stock. */
    void assertVerifiedInstitution(User institution);
}
