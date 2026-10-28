package com.suwasarana.api.admin;

import com.suwasarana.api.admin.dto.ReportDto;
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

    /**
     * Kept for older callers (the AI intake service). The single source of truth is
     * {@link com.suwasarana.api.geo.District}; this is a read-only view of it.
     */
    public static final Map<String, double[]> DISTRICT_COORDINATES = com.suwasarana.api.geo.District.coordinateMap();

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RequestReportRepository requestReportRepository;

    @Autowired
    private RequestRepository requestRepository;

    @Autowired
    private DonorRepository donorRepository;

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

    public List<BloodRequest> getTriageQueue() {
        return requestRepository.findAllByOrderByFraudRiskScoreDesc();
    }

    @Transactional
    public void reviewTriageRequest(Long requestId, String adminNotes) {
        BloodRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> ApiException.notFound("Request not found"));
        request.setFraudRiskScore(0);
        // Keep the original AI reason and append the admin note (column is VARCHAR(255))
        String note = " | Admin: " + (adminNotes != null && !adminNotes.isBlank() ? adminNotes.trim() : "reviewed");
        if (note.length() > 255) {
            note = note.substring(0, 255);
        }
        String original = request.getAiFlagReason() != null ? request.getAiFlagReason() : "";
        if (original.length() + note.length() > 255) {
            original = original.substring(0, 255 - note.length());
        }
        request.setAiFlagReason(original + note);
        requestRepository.save(request);
    }
}
