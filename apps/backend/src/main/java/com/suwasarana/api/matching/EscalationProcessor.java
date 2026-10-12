package com.suwasarana.api.matching;

import com.suwasarana.api.request.BloodRequest;
import com.suwasarana.api.request.RequestRepository;
import com.suwasarana.api.request.RequestStatus;
import com.suwasarana.api.request.Urgency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Handles one request per transaction so a failure while escalating one request
 * does not roll back the others (see EscalationScheduler).
 */
@Service
public class EscalationProcessor {

    private static final Logger log = LoggerFactory.getLogger(EscalationProcessor.class);
    private static final short MAX_RADIUS_KM = 50;
    private static final short RADIUS_INCREMENT = 10;

    @Autowired
    private RequestRepository requestRepository;

    @Autowired
    private MatchingEngine matchingEngine;

    @Transactional
    public void processRequest(Long requestId) {
        BloodRequest request = requestRepository.findById(requestId).orElse(null);
        if (request == null
                || (request.getStatus() != RequestStatus.OPEN && request.getStatus() != RequestStatus.ESCALATING)) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        if (request.getExpiresAt().isBefore(now)) {
            request.setStatus(RequestStatus.EXPIRED);
            requestRepository.save(request);
            log.info("Request {} expired", request.getId());
            return;
        }

        // Severity-based timeouts
        long minutesSinceLastEscalation = Duration.between(request.getLastEscalatedAt(), now).toMinutes();
        boolean shouldEscalate = false;

        if (request.getUrgency() == Urgency.CRITICAL && minutesSinceLastEscalation >= 5) {
            shouldEscalate = true;
        } else if (request.getUrgency() == Urgency.URGENT && minutesSinceLastEscalation >= 10) {
            shouldEscalate = true;
        } else if (request.getUrgency() == Urgency.ROUTINE && minutesSinceLastEscalation >= 20) {
            shouldEscalate = true;
        }

        if (shouldEscalate && request.getCurrentRadiusKm() < MAX_RADIUS_KM) {
            short newRadius = (short) Math.min(request.getCurrentRadiusKm() + RADIUS_INCREMENT, MAX_RADIUS_KM);
            request.setCurrentRadiusKm(newRadius);
            request.setLastEscalatedAt(now);
            request.setStatus(RequestStatus.ESCALATING);
            requestRepository.save(request);

            log.info("Escalated Request {} (Urgency: {}) radius to {}km", request.getId(), request.getUrgency(), newRadius);

            // Run matching engine to find donors in the new wider radius
            matchingEngine.runMatchingForRequest(request.getId());
        }
    }
}
