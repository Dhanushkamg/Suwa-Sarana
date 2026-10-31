package com.suwasarana.api.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.suwasarana.api.ai.TriageResult.Reason;
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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Rule-based (not machine-learning) triage of new blood requests: it scores how much a request deserves a human look.
 * Every score is explained by an ordered list of reasons ({@link TriageResult}), stored append-only in
 * {@code ai_triage_records}, and never changes the request's status. A reviewer's decision is recorded next to the
 * original score with {@link #recordReview}; the score is not zeroed.
 */
@Service
public class RequestTriageAiService {

    private static final Logger log = LoggerFactory.getLogger(RequestTriageAiService.class);

    @Autowired
    private RequestRepository requestRepository;

    @Autowired(required = false)
    private AiTriageRecordRepository recordRepository;

    private final ObjectMapper mapper = new ObjectMapper();

    /** Computes the explained score without saving anything. */
    public TriageResult evaluate(BloodRequest request) {
        List<Reason> reasons = new ArrayList<>();
        User requester = request.getRequester();
        boolean isVerified = requester != null && requester.getVerificationStatus() == VerificationStatus.VERIFIED;

        if (requester != null && requester.getId() != null) {
            LocalDateTime dayAgo = LocalDateTime.now().minusHours(24);
            long recentCount = requestRepository.findByRequesterId(requester.getId()).stream()
                    .filter(r -> r.getCreatedAt() != null && r.getCreatedAt().isAfter(dayAgo)
                            && r.getId() != null && !r.getId().equals(request.getId()))
                    .count();
            if (recentCount >= 2) {
                reasons.add(new Reason("REQUESTER_HIGH_FREQUENCY", 45,
                        "High frequency: " + (recentCount + 1) + " requests created in 24h", Map.of("count", recentCount + 1)));
            } else if (recentCount == 1) {
                reasons.add(new Reason("REQUESTER_REPEAT_24H", 20, "Previous request created within 24h", Map.of()));
            }
        }

        int units = request.getUnitsNeeded();
        if (units >= 6 && !isVerified) {
            reasons.add(new Reason("UNVERIFIED_HIGH_UNITS", 35, "High unit volume (" + units + " units) by unverified account", Map.of("units", units)));
        } else if (units >= 4 && !isVerified) {
            reasons.add(new Reason("UNVERIFIED_MODERATE_UNITS", 15, "Moderate unit volume (" + units + " units)", Map.of("units", units)));
        }

        String hosp = request.getHospitalName() != null ? request.getHospitalName().trim() : "";
        if (hosp.length() < 5 || hosp.equalsIgnoreCase("test") || hosp.equalsIgnoreCase("hospital") || hosp.equalsIgnoreCase("general")) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("name", hosp);
            reasons.add(new Reason("SUSPICIOUS_HOSPITAL_NAME", 30, "Suspicious or abbreviated hospital name ('" + hosp + "')", p));
        }

        if (request.getUrgency() == Urgency.CRITICAL && !isVerified) {
            reasons.add(new Reason("UNVERIFIED_CRITICAL", 25, "CRITICAL urgency requested by unverified account", Map.of()));
        }

        reasons.sort(Comparator.comparingInt(Reason::weight).reversed());
        int raw = reasons.stream().mapToInt(Reason::weight).sum();
        int score = Math.min(100, Math.max(0, raw));
        return new TriageResult(score, raw, List.copyOf(reasons), TriageResult.ENGINE_VERSION, LocalDateTime.now(),
                score >= TriageResult.REVIEW_THRESHOLD);
    }

    /**
     * Evaluates, stores the explained result and fills the legacy request columns (score, short reason text, time).
     * The request status is never touched.
     */
    @Transactional
    public BloodRequest triageRequest(BloodRequest request) {
        if (request == null) return null;
        TriageResult result = evaluate(request);
        String reason = result.reasons().isEmpty() ? "Standard requisition profile. No anomalies detected."
                : String.join("; ", result.reasons().stream().map(Reason::detail).toList());
        if (reason.length() > 255) reason = reason.substring(0, 255);

        request.setFraudRiskScore(result.score());
        request.setAiFlagReason(reason);
        request.setTriagedAt(result.evaluatedAt());
        store(request.getId(), result);
        log.info("AI triage request={} score={} reasons={}", request.getId(), result.score(), result.reasons().stream().map(Reason::code).toList());
        return requestRepository.save(request);
    }

    private void store(Long requestId, TriageResult result) {
        if (recordRepository == null || requestId == null) return;
        try {
            AiTriageRecord rec = new AiTriageRecord();
            rec.setRequestId(requestId);
            rec.setScore(result.score());
            rec.setRawScore(result.rawScore());
            rec.setEngineVersion(result.engineVersion());
            rec.setCreatedAt(result.evaluatedAt());
            rec.setReasonsJson(mapper.writeValueAsString(result.reasons()));
            recordRepository.save(rec);
        } catch (Exception e) {
            // a failed audit write must never block request creation, but it must be visible
            log.error("Could not store AI triage record for request {}: {}", requestId, e.getClass().getSimpleName());
        }
    }

    /** The latest stored result of a request, with its reasons, or empty when it was never triaged. */
    @Transactional(readOnly = true)
    public Optional<TriageResult> latestResult(Long requestId) {
        if (recordRepository == null) return Optional.empty();
        return recordRepository.findFirstByRequestIdOrderByCreatedAtDescIdDesc(requestId).map(this::toResult);
    }

    private TriageResult toResult(AiTriageRecord rec) {
        List<Reason> reasons;
        try {
            reasons = mapper.readValue(rec.getReasonsJson(), new TypeReference<List<Reason>>() { });
        } catch (Exception e) {
            reasons = List.of();
        }
        return new TriageResult(rec.getScore(), rec.getRawScore(), reasons, rec.getEngineVersion(), rec.getCreatedAt(),
                rec.getScore() >= TriageResult.REVIEW_THRESHOLD);
    }

    /**
     * Records that a person reviewed the latest AI result: who, when, the decision (for example DISMISSED or CONFIRMED)
     * and a note. The AI score and reasons stay untouched. Called by the admin service instead of zeroing the score.
     *
     * @return true when a stored result existed to attach the review to
     */
    @Transactional
    public boolean recordReview(Long requestId, Long reviewerUserId, String decision, String note) {
        if (recordRepository == null) return false;
        Optional<AiTriageRecord> latest = recordRepository.findFirstByRequestIdOrderByCreatedAtDescIdDesc(requestId);
        if (latest.isEmpty()) return false;
        AiTriageRecord rec = latest.get();
        rec.setReviewedBy(reviewerUserId);
        rec.setReviewedAt(LocalDateTime.now());
        rec.setReviewDecision(decision == null || decision.isBlank() ? "REVIEWED" : decision.trim().toUpperCase());
        String n = note == null ? null : note.trim();
        rec.setReviewNote(n == null || n.isEmpty() ? null : (n.length() > 500 ? n.substring(0, 500) : n));
        recordRepository.save(rec);
        return true;
    }
}
