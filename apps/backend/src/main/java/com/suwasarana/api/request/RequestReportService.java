package com.suwasarana.api.request;

import com.suwasarana.api.request.dto.ReportRequestDto;
import com.suwasarana.api.shared.AppClock;
import com.suwasarana.api.common.ApiException;
import com.suwasarana.api.common.ErrorCodes;
import com.suwasarana.api.user.User;
import com.suwasarana.api.user.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Any signed-in user may report a request once. Reports feed the admin moderation list
 * ({@code GET /api/admin/reports}); they never change the request by themselves.
 */
@Service
public class RequestReportService {

    @Autowired
    private RequestRepository requestRepository;

    @Autowired
    private RequestReportRepository reportRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AppClock clock;

    @Transactional
    public void report(Long requestId, Long reporterId, ReportRequestDto dto) {
        BloodRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> ApiException.notFound(ErrorCodes.REQUEST_NOT_FOUND, "Request not found"));
        if (request.getRequester().getId().equals(reporterId)) {
            throw ApiException.badRequest(ErrorCodes.CANNOT_REPORT_OWN_REQUEST, "You cannot report your own request");
        }
        if (reportRepository.existsByRequestIdAndReportedById(requestId, reporterId)) {
            throw ApiException.conflict(ErrorCodes.REPORT_ALREADY_FILED, "You have already reported this request");
        }
        User reporter = userRepository.findById(reporterId)
                .orElseThrow(() -> ApiException.unauthorized(ErrorCodes.AUTH_UNAUTHORIZED, "User not found"));

        String details = dto.getDetails() == null ? null : dto.getDetails().trim();
        if (details != null && details.isEmpty()) details = null;

        RequestReport report = new RequestReport();
        report.setRequest(request);
        report.setReportedBy(reporter);
        report.setReasonCode(dto.getReason().name());
        report.setDetails(details);
        // The legacy "reason" column (shown in the admin list) keeps the readable form
        report.setReason(details == null ? dto.getReason().name() : dto.getReason().name() + ": " + details);
        report.setCreatedAt(clock.instant());
        try {
            reportRepository.saveAndFlush(report);
        } catch (DataIntegrityViolationException e) {
            // Two simultaneous submissions: the unique index (request_id, reported_by) decides
            throw ApiException.conflict(ErrorCodes.REPORT_ALREADY_FILED, "You have already reported this request");
        }
    }
}
