package com.suwasarana.api.admin;

import com.suwasarana.api.admin.dto.ReportDto;
import com.suwasarana.api.admin.dto.TriageQueueItemDto;
import com.suwasarana.api.ai.RequestTriageAiService;
import com.suwasarana.api.audit.AdminAuditService;
import com.suwasarana.api.audit.AuditAction;
import com.suwasarana.api.user.Role;
import com.suwasarana.api.common.ApiException;
import com.suwasarana.api.donor.DonorProfile;
import com.suwasarana.api.donor.DonorRepository;
import com.suwasarana.api.request.BloodRequest;
import com.suwasarana.api.request.RequestReport;
import com.suwasarana.api.request.RequestReportRepository;
import com.suwasarana.api.request.RequestRepository;
import com.suwasarana.api.request.RequestStatus;
import com.suwasarana.api.user.User;
import com.suwasarana.api.user.UserRepository;
import com.suwasarana.api.user.VerificationStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class AdminService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RequestReportRepository requestReportRepository;

    @Autowired
    private RequestRepository requestRepository;

    @Autowired
    private DonorRepository donorRepository;

    @Autowired
    private RequestTriageAiService triageService;

    @Autowired
    private AdminAuditService audit;

    @Transactional(readOnly = true)
    public List<ReportDto> getReports() {
        return requestReportRepository.findAll().stream().map(this::mapToReportDto).toList();
    }

    private ReportDto mapToReportDto(RequestReport report) {
        ReportDto dto = new ReportDto();
        dto.setId(report.getId());
        dto.setRequestId(report.getRequest().getId());
        dto.setReason(report.getReason());
        dto.setCreatedAt(report.getCreatedAt());
        dto.setReporterId(report.getReportedBy().getId());
        return dto;
    }

    /** Highest AI score first; requests a person already reviewed are listed after the ones still waiting. */
    @Transactional(readOnly = true)
    public List<TriageQueueItemDto> getTriageQueue() {
        List<TriageQueueItemDto> items = new ArrayList<>();
        for (BloodRequest r : requestRepository.findAllByOrderByFraudRiskScoreDesc()) {
            items.add(toQueueItem(r));
        }
        // stable sort: the repository order (score desc) is kept inside each group
        items.sort(Comparator.comparing(TriageQueueItemDto::isReviewed));
        return items;
    }

    private TriageQueueItemDto toQueueItem(BloodRequest request) {
        TriageQueueItemDto dto = new TriageQueueItemDto();
        dto.setId(request.getId());
        dto.setPatientBloodType(request.getPatientBloodType());
        dto.setUnitsNeeded(request.getUnitsNeeded());
        dto.setUrgency(request.getUrgency());
        dto.setHospitalName(request.getHospitalName());
        dto.setDistrict(request.getDistrict());
        dto.setLatitude(request.getLatitude());
        dto.setLongitude(request.getLongitude());
        dto.setStatus(request.getStatus());
        dto.setCurrentRadiusKm(request.getCurrentRadiusKm());
        dto.setCreatedAt(request.getCreatedAt());
        dto.setExpiresAt(request.getExpiresAt());
        dto.setFraudRiskScore(request.getFraudRiskScore());
        dto.setAiFlagReason(request.getAiFlagReason());
        dto.setTriagedAt(request.getTriagedAt());
        triageService.latestResult(request.getId()).ifPresent(result -> {
            dto.setReasons(result.reasons().stream().map(x -> new TriageQueueItemDto.Reason(x.code(), x.weight())).toList());
            dto.setEngineVersion(result.engineVersion());
            dto.setHumanReviewRecommended(result.humanReviewRecommended());
        });
        triageService.latestReview(request.getId()).ifPresent(rv -> {
            dto.setReviewed(true);
            dto.setReviewedBy(rv.reviewedBy());
            dto.setReviewedAt(rv.reviewedAt());
            dto.setReviewDecision(rv.decision());
            dto.setReviewNote(rv.note());
        });
        return dto;
    }

    /**
     * A person looked at the AI flag. The AI score and reasons stay exactly as computed: the reviewer, time, decision and
     * note are stored beside them (RequestTriageAiService.recordReview) and the action is written to the append-only
     * admin audit log.
     */
    @Transactional
    public void reviewTriageRequest(Long requestId, Long reviewerId, String decision, String adminNotes, String ip) {
        BloodRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> ApiException.notFound("request.not_found", "Request not found"));
        String d = decision == null || decision.isBlank() ? "REVIEWED" : decision.trim().toUpperCase(Locale.ROOT);
        String note = adminNotes == null || adminNotes.isBlank() ? null : adminNotes.trim();
        // a request created before triage records existed gets its explained result first, so the review has something to attach to
        if (triageService.latestResult(requestId).isEmpty()) {
            triageService.triageRequest(request);
        }
        triageService.recordReview(requestId, reviewerId, d, note);
        Long requesterId = request.getRequester() == null ? null : request.getRequester().getId();
        audit.record(reviewerId, Role.ADMIN, AuditAction.TRIAGE_REVIEWED, requesterId, note,
                "requestId=" + requestId + " decision=" + d + " score=" + request.getFraudRiskScore(), ip);
    }
}
