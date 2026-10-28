package com.suwasarana.api.request;

import com.suwasarana.api.exception.RequesterNotVerifiedException;
import com.suwasarana.api.shared.CodedApiException;
import com.suwasarana.api.shared.ErrorCodes;
import com.suwasarana.api.user.User;
import com.suwasarana.api.user.VerificationStatus;
import org.springframework.stereotype.Component;

/**
 * Interim rules, until the shared verification guard exists:
 * <ul>
 *   <li>REJECTED accounts cannot raise requests at all;</li>
 *   <li>CRITICAL requests (which alert donors fastest and widest) need a VERIFIED account;</li>
 *   <li>stock can be published only by VERIFIED institutions.</li>
 * </ul>
 */
@Component
public class DefaultRequesterEligibility implements RequesterEligibility {

    @Override
    public void assertMayCreateRequest(User requester, Urgency urgency) {
        if (requester.getVerificationStatus() == VerificationStatus.REJECTED) {
            throw CodedApiException.forbidden(ErrorCodes.REQUESTER_NOT_ALLOWED,
                    "The verification of this account was rejected, so it cannot create blood requests.");
        }
        if (urgency == Urgency.CRITICAL && requester.getVerificationStatus() != VerificationStatus.VERIFIED) {
            throw new RequesterNotVerifiedException("Unverified users cannot create CRITICAL requests.");
        }
    }

    @Override
    public void assertVerifiedInstitution(User institution) {
        if (institution.getVerificationStatus() != VerificationStatus.VERIFIED) {
            throw CodedApiException.forbidden(ErrorCodes.INSTITUTION_NOT_VERIFIED,
                    "Only verified hospitals and blood banks can manage blood stock.");
        }
    }
}
