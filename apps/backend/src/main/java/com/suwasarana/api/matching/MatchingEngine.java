package com.suwasarana.api.matching;

import com.suwasarana.api.donor.DeferralService;
import com.suwasarana.api.donor.DonorProfile;
import com.suwasarana.api.notification.NotificationMessage;
import com.suwasarana.api.notification.NotificationService;
import com.suwasarana.api.request.BloodRequest;
import com.suwasarana.api.request.RequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class MatchingEngine {

    private static final Logger log = LoggerFactory.getLogger(MatchingEngine.class);

    @Autowired
    private MatchingEngineRepository matchingEngineRepository;

    @Autowired
    private RequestMatchRepository requestMatchRepository;

    @Autowired
    private RequestRepository requestRepository;

    @Autowired
    private DeferralService deferralService;

    @Autowired
    private NotificationCascadeService notificationCascadeService;

    @Transactional
    public void runMatchingForRequest(Long requestId) {
        BloodRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Request not found"));

        // Fetch top 20 candidates within radius
        List<DonorProfile> candidates = matchingEngineRepository.findEligibleDonorsWithinRadius(
                request.getPatientBloodType(),
                request.getLatitude(),
                request.getLongitude(),
                request.getCurrentRadiusKm(),
                request.getId(),
                20
        );

        int matchedCount = 0;
        LocalDate today = LocalDate.now();

        for (DonorProfile candidate : candidates) {
            // Check eligibility (age, weight, active deferrals)
            if (deferralService.checkEligibility(candidate, today).isEligible()) {
                
                RequestMatch match = new RequestMatch();
                match.setRequest(request);
                match.setDonor(candidate);
                match.setStatus(MatchStatus.PENDING);
                
                requestMatchRepository.save(match);
                matchedCount++;
                
                // 3. Trigger SSE/SMS
                notificationCascadeService.sendNotification(candidate, request);
                log.info("Matched Request {} with Donor {}", request.getId(), candidate.getId());
            }
        }

        log.info("Matched {} donors for Request ID {}", matchedCount, request.getId());
    }
}
