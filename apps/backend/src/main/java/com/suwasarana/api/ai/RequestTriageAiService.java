package com.suwasarana.api.ai;

import com.suwasarana.api.request.BloodRequest;
import com.suwasarana.api.request.RequestRepository;
import com.suwasarana.api.request.Urgency;
import com.suwasarana.api.user.User;
import com.suwasarana.api.user.VerificationStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class RequestTriageAiService {

    private static final Logger log = LoggerFactory.getLogger(RequestTriageAiService.class);

    @Autowired
    private RequestRepository requestRepository;

    @Transactional
    public BloodRequest triageRequest(BloodRequest request) {
        if (request == null) return null;

        int score = 0;
        List<String> flags = new ArrayList<>();

        User requester = request.getRequester();
        boolean isVerified = requester != null && requester.getVerificationStatus() == VerificationStatus.VERIFIED;

        // 1. Rapid Duplicate / Frequency Check
        if (requester != null && requester.getId() != null) {
            LocalDateTime dayAgo = LocalDateTime.now().minusHours(24);
            List<BloodRequest> recentRequests = requestRepository.findByRequesterId(requester.getId());
            long recentCount = recentRequests.stream()
                    .filter(r -> r.getCreatedAt() != null && r.getCreatedAt().isAfter(dayAgo) && !r.getId().equals(request.getId()))
                    .count();

            if (recentCount >= 2) {
                score += 45;
                flags.add("High frequency: " + (recentCount + 1) + " requests created in 24h");
            } else if (recentCount == 1) {
                score += 20;
                flags.add("Previous request created within 24h");
            }
        }

        // 2. High Volume Request by Unverified User
        if (request.getUnitsNeeded() >= 6 && !isVerified) {
            score += 35;
            flags.add("High unit volume (" + request.getUnitsNeeded() + " units) by unverified account");
        } else if (request.getUnitsNeeded() >= 4 && !isVerified) {
            score += 15;
            flags.add("Moderate unit volume (" + request.getUnitsNeeded() + " units)");
        }

        // 3. Hospital Facility Name Anomaly Check
        String hosp = request.getHospitalName() != null ? request.getHospitalName().trim() : "";
        if (hosp.length() < 5 || hosp.equalsIgnoreCase("test") || hosp.equalsIgnoreCase("hospital") || hosp.equalsIgnoreCase("general")) {
            score += 30;
            flags.add("Suspicious or abbreviated hospital name ('" + hosp + "')");
        }

        // 4. Critical Urgency Unverified Flag
        if (request.getUrgency() == Urgency.CRITICAL && !isVerified) {
            score += 25;
            flags.add("CRITICAL urgency requested by unverified account");
        }

        // Bound final risk score between 0 and 100
        int finalScore = Math.min(100, Math.max(0, score));
        String reason = flags.isEmpty() ? "Standard requisition profile. No anomalies detected." : String.join("; ", flags);

        request.setFraudRiskScore(finalScore);
        request.setAiFlagReason(reason);
        request.setTriagedAt(LocalDateTime.now());

        // Note: Lifecycle status is deliberately NEVER mutated by AI triage
        log.info("AI Triage evaluated Request #{} with Fraud Risk Score: {} (Reason: {})", request.getId(), finalScore, reason);
        return requestRepository.save(request);
    }
}
