package com.suwasarana.api.matching;

import com.suwasarana.api.common.ApiException;
import com.suwasarana.api.donor.DeferralService;
import com.suwasarana.api.donor.DonorProfile;
import com.suwasarana.api.donor.DonorRepository;
import com.suwasarana.api.request.BloodRequest;
import com.suwasarana.api.request.RequestRepository;
import com.suwasarana.api.request.RequestStatus;
import com.suwasarana.api.request.Urgency;
import com.suwasarana.api.shared.AppClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Finds donors for a request and records the matches. Notifications are NOT sent here: a
 * {@link DonorsMatchedEvent} is published and handled after the surrounding transaction commits.
 *
 * <p>Pipeline: ABO/Rh-compatible donor types ({@link BloodCompatibility}) within the current radius (GiST-indexed
 * {@code earth_box} prefilter), up to {@link #CANDIDATE_POOL} rows ranked in SQL (nearest first for CRITICAL requests,
 * reliability-weighted otherwise), then the NBTS eligibility rules from {@code DeferralService} are applied and the
 * first {@link #MAX_MATCHES_PER_RUN} eligible donors are matched.
 */
@Service
public class MatchingEngine {

    private static final Logger log = LoggerFactory.getLogger(MatchingEngine.class);

    /** Rows fetched per run; more than we match so that ineligible donors (age, weight) do not starve the result. */
    static final int CANDIDATE_POOL = 100;
    static final int MAX_MATCHES_PER_RUN = 20;

    @Autowired
    private MatchingEngineRepository matchingEngineRepository;

    @Autowired
    private RequestMatchRepository requestMatchRepository;

    @Autowired
    private RequestRepository requestRepository;

    @Autowired
    private DonorRepository donorRepository;

    @Autowired
    private DeferralService deferralService;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private AppClock clock;

    @Transactional
    public void runMatchingForRequest(Long requestId) {
        BloodRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> ApiException.notFound("Request not found"));

        if (request.getStatus() != RequestStatus.OPEN && request.getStatus() != RequestStatus.ESCALATING) {
            log.info("Skipping matching for request {} in status {}", requestId, request.getStatus());
            return;
        }

        LocalDate today = clock.today();
        boolean critical = request.getUrgency() == Urgency.CRITICAL;
        List<String> compatibleTypes = BloodCompatibility.compatibleDonorTypes(request.getPatientBloodType());
        if (compatibleTypes.isEmpty()) {
            log.warn("Request {} has an unknown blood type '{}'; no matching done", requestId, request.getPatientBloodType());
            return;
        }

        List<Object[]> rows = matchingEngineRepository.findCandidateRows(
                compatibleTypes,
                request.getPatientBloodType(),
                request.getLatitude(),
                request.getLongitude(),
                request.getCurrentRadiusKm() * 1000.0,
                request.getId(),
                today,
                critical,
                CANDIDATE_POOL);

        List<Long> orderedIds = new ArrayList<>();
        Map<Long, BigDecimal> distanceById = new HashMap<>();
        for (Object[] row : rows) {
            Long id = ((Number) row[0]).longValue();
            orderedIds.add(id);
            distanceById.put(id, BigDecimal.valueOf(((Number) row[1]).doubleValue()).setScale(2, RoundingMode.HALF_UP));
        }
        Map<Long, DonorProfile> profiles = new HashMap<>();
        donorRepository.findAllById(orderedIds).forEach(p -> profiles.put(p.getId(), p));

        List<Long> matchIds = new ArrayList<>();
        for (Long donorId : orderedIds) {
            if (matchIds.size() >= MAX_MATCHES_PER_RUN) break;
            DonorProfile candidate = profiles.get(donorId);
            if (candidate == null || !deferralService.checkEligibility(candidate, today).isEligible()) continue;

            RequestMatch match = new RequestMatch();
            match.setRequest(request);
            match.setDonor(candidate);
            match.setStatus(MatchStatus.PENDING);
            match.setDistanceKm(distanceById.get(donorId));
            match.setCreatedAt(clock.utcNow());
            matchIds.add(requestMatchRepository.save(match).getId());
            log.info("Matched Request {} with Donor {} ({} km)", request.getId(), candidate.getId(), distanceById.get(donorId));
        }

        log.info("Matched {} donors for Request ID {}", matchIds.size(), request.getId());
        if (!matchIds.isEmpty()) {
            eventPublisher.publishEvent(new DonorsMatchedEvent(request.getId(), List.copyOf(matchIds)));
        }
    }
}
