package com.suwasarana.api.matching;

import com.suwasarana.api.common.ApiException;
import com.suwasarana.api.donor.DeferralRepository;
import com.suwasarana.api.donor.DeferralRulesConfig;
import com.suwasarana.api.donor.DonorDeferral;
import com.suwasarana.api.donor.DonorProfile;
import com.suwasarana.api.donor.ReliabilityScoreService;
import com.suwasarana.api.request.RequestRepository;
import com.suwasarana.api.request.BloodRequest;
import com.suwasarana.api.request.RequestStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
public class MatchService {

    private static final int LIVES_HELPED_PER_DONATION = 3;

    @Autowired
    private RequestMatchRepository requestMatchRepository;

    @Autowired
    private RequestRepository requestRepository;

    @Autowired
    private ReliabilityScoreService reliabilityScoreService;

    @Autowired
    private DeferralRepository deferralRepository;

    @Transactional
    public void respondToMatch(Long matchId, Long donorId, MatchStatus response) {
        if (response != MatchStatus.ACCEPTED && response != MatchStatus.DECLINED) {
            throw ApiException.badRequest("Response must be ACCEPTED or DECLINED");
        }

        RequestMatch match = requestMatchRepository.findById(matchId)
                .orElseThrow(() -> ApiException.notFound("Match not found"));

        if (!match.getDonor().getUser().getId().equals(donorId)) {
            throw ApiException.forbidden("Unauthorized to respond to this match");
        }

        BloodRequest request = match.getRequest();
        if (match.getStatus() != MatchStatus.PENDING) {
            throw ApiException.conflict("This match has already been responded to");
        }
        if (request.getStatus() != RequestStatus.OPEN && request.getStatus() != RequestStatus.ESCALATING) {
            throw ApiException.conflict("This request is no longer active");
        }

        match.setStatus(response);
        match.setRespondedAt(java.time.LocalDateTime.now());
        
        requestMatchRepository.save(match);

        // If ACCEPTED, check if the request is now FULFILLED
        if (response == MatchStatus.ACCEPTED) {
            long acceptedCount = requestMatchRepository.countByRequestAndStatus(request, MatchStatus.ACCEPTED);
            
            if (acceptedCount >= request.getUnitsNeeded()) {
                request.setStatus(RequestStatus.MATCHED); // Fully matched
                requestRepository.save(request);
            }
        }
    }

    @Transactional
    public void markMatchDonated(Long matchId, Long requesterId, boolean isAdmin, boolean donated) {
        RequestMatch match = requestMatchRepository.findById(matchId)
                .orElseThrow(() -> ApiException.notFound("Match not found"));

        if (!isAdmin && !match.getRequest().getRequester().getId().equals(requesterId)) {
            throw ApiException.forbidden("Unauthorized to confirm donation for this request");
        }
        if (match.getStatus() != MatchStatus.ACCEPTED) {
            throw ApiException.conflict("Only accepted matches can be marked as donated");
        }
        if (match.getDonated() != null) {
            throw ApiException.conflict("Donation outcome has already been recorded");
        }

        match.setDonated(donated);
        requestMatchRepository.save(match);

        // Adjust Reliability Score based on donation truth
        if (donated) {
            reliabilityScoreService.recordSuccessfulDonation(match.getDonor().getId());
            recordDonationOnProfile(match.getDonor());
        } else {
            // Accepted but didn't show up -> penalize
            reliabilityScoreService.recordNoShow(match.getDonor().getId());
        }
    }

    // Update donor totals and defer the donor until the minimum donation interval has passed
    private void recordDonationOnProfile(DonorProfile donor) {
        donor.setTotalDonations(donor.getTotalDonations() + 1);
        donor.setLivesHelpedEstimate(donor.getLivesHelpedEstimate() + LIVES_HELPED_PER_DONATION);

        LocalDate today = LocalDate.now();
        DonorDeferral deferral = new DonorDeferral();
        deferral.setDonor(donor);
        deferral.setReason("RECENT_DONATION");
        deferral.setDeferredFrom(today);
        deferral.setDeferredUntil(today.plusDays(DeferralRulesConfig.DONATION_INTERVAL_DAYS));
        deferralRepository.save(deferral);
    }
}
