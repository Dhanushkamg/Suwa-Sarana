package com.suwasarana.api.request;

import com.suwasarana.api.user.User;

/**
 * Who may raise requests and publish institution data. A deliberately tiny seam: the account/verification work
 * provides a VerificationGuard, and once it is merged an implementation of this interface should delegate to it
 * (see {@link DefaultRequesterEligibility}, which holds the rules in the meantime).
 */
public interface RequesterEligibility {

    /** Throws when {@code requester} may not create a request of this urgency. */
    void assertMayCreateRequest(User requester, Urgency urgency);

    /** Throws unless {@code institution} is a VERIFIED hospital / blood bank allowed to publish stock. */
    void assertVerifiedInstitution(User institution);
}
