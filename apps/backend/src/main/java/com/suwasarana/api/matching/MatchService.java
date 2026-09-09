package com.suwasarana.api.matching;

import com.suwasarana.api.donor.ReliabilityScoreService;
import com.suwasarana.api.request.RequestRepository;
import com.suwasarana.api.request.BloodRequest;
import com.suwasarana.api.request.RequestStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MatchService {

    @Autowired
    private RequestMatchRepository requestMatchRepository;

    @Autowired
    private RequestRepository requestRepository;

    @Autowired
    private ReliabilityScoreService reliabilityScoreService;

    @Transactional
    public void respondToMatch(Long matchId, Long donorId, MatchStatus response) {
        RequestMatch match = requestMatchRepository.findById(matchId)
                .orElseThrow(() -> new RuntimeException("Match not found"));

        if (!match.getDonor().getUser().getId().equals(donorId)) {
            throw new RuntimeException("Unauthorized to respond to this match");
        }

        match.setStatus(response);
        match.setRespondedAt(java.time.LocalDateTime.now());
        
        requestMatchRepository.save(match);

        // If ACCEPTED, check if the request is now FULFILLED
        if (response == MatchStatus.ACCEPTED) {
            BloodRequest request = match.getRequest();
            long acceptedCount = requestMatchRepository.countByRequestAndStatus(request, MatchStatus.ACCEPTED);
            
            if (acceptedCount >= request.getUnitsNeeded()) {
                request.setStatus(RequestStatus.MATCHED); // Fully matched
                requestRepository.save(request);
            }
        }
    }

    @Transactional
    public void markMatchDonated(Long matchId, Long requesterId, boolean donated) {
        RequestMatch match = requestMatchRepository.findById(matchId)
                .orElseThrow(() -> new RuntimeException("Match not found"));

        if (!match.getRequest().getRequester().getId().equals(requesterId)) {
            throw new RuntimeException("Unauthorized to confirm donation for this request");
        }

        match.setDonated(donated);
        requestMatchRepository.save(match);

        // Adjust Reliability Score based on donation truth
        if (donated) {
            reliabilityScoreService.recordSuccessfulDonation(match.getDonor().getId());
        } else if (match.getStatus() == MatchStatus.ACCEPTED) {
            // Accepted but didn't show up -> penalize
            reliabilityScoreService.recordNoShow(match.getDonor().getId());
        }
    }
}
