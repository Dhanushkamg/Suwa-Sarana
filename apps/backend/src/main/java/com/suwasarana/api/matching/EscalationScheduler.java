package com.suwasarana.api.matching;

import com.suwasarana.api.request.BloodRequest;
import com.suwasarana.api.request.RequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class EscalationScheduler {

    private static final Logger log = LoggerFactory.getLogger(EscalationScheduler.class);
    private static final short MAX_RADIUS_KM = 50;
    private static final short RADIUS_INCREMENT = 10;

    @Autowired
    private RequestRepository requestRepository;

    @Autowired
    private MatchingEngine matchingEngine;

    // Run every 15 minutes
    @Scheduled(cron = "0 0/15 * * * *")
    @Transactional
    public void processEscalations() {
        log.info("Running escalation scheduler...");

        List<BloodRequest> activeRequests = requestRepository.findActiveRequests();
        LocalDateTime now = LocalDateTime.now();

        for (BloodRequest request : activeRequests) {
            if (request.getExpiresAt().isBefore(now)) {
                request.setStatus(com.suwasarana.api.request.RequestStatus.EXPIRED);
                requestRepository.save(request);
                log.info("Request {} expired", request.getId());
                continue;
            }

            // Simple escalation logic: If it's been OPEN/ESCALATING for a while and not fulfilled, expand radius
            if (request.getCurrentRadiusKm() < MAX_RADIUS_KM) {
                short newRadius = (short) Math.min(request.getCurrentRadiusKm() + RADIUS_INCREMENT, MAX_RADIUS_KM);
                request.setCurrentRadiusKm(newRadius);
                request.setStatus(com.suwasarana.api.request.RequestStatus.ESCALATING);
                requestRepository.save(request);

                log.info("Escalated Request {} radius to {}km", request.getId(), newRadius);

                // Run matching engine to find donors in the new wider radius
                matchingEngine.runMatchingForRequest(request.getId());
            }
        }
    }
}
