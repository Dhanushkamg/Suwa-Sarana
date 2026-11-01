package com.suwasarana.api.admin;

import com.suwasarana.api.admin.dto.ReportDto;
import com.suwasarana.api.admin.dto.TriageQueueItemDto;
import com.suwasarana.api.admin.dto.TriageReviewDto;
import com.suwasarana.api.security.ClientIp;
import com.suwasarana.api.security.UserDetailsImpl;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.suwasarana.api.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Operations", description = "Administrative analytics, district summaries, moderation reports, and AI fraud triage")
public class AdminController {

    @Autowired
    private AdminService adminService;

    @Operation(summary = "Get flagged reports", description = "Retrieves abuse reports and flagged items submitted by community users.")
    @GetMapping("/reports")
    public ResponseEntity<ApiResponse<List<ReportDto>>> getReports() {
        return ResponseEntity.ok(ApiResponse.success(adminService.getReports()));
    }

    @Operation(summary = "Get AI triage queue", description = "Active requisitions prioritised by AI risk score, each with the reasons (code + weight) behind the score; "
            + "requests a person already reviewed are listed after the pending ones. The score is never changed by a review.")
    @GetMapping("/triage/queue")
    public ResponseEntity<ApiResponse<List<TriageQueueItemDto>>> getTriageQueue() {
        return ResponseEntity.ok(ApiResponse.success(adminService.getTriageQueue()));
    }

    @Operation(summary = "Review triaged request", description = "Records who reviewed the AI flag, when, the decision (REVIEWED, DISMISSED or CONFIRMED) and a note, "
            + "beside the untouched AI score, and writes an audit-log entry.")
    @PutMapping("/triage/{id}/review")
    public ResponseEntity<ApiResponse<Void>> reviewTriage(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) TriageReviewDto body,
            @AuthenticationPrincipal UserDetailsImpl admin,
            HttpServletRequest http) {
        adminService.reviewTriageRequest(id, admin.getId(), body != null ? body.getDecision() : null,
                body != null ? body.getNotes() : null, ClientIp.of(http));
        return ResponseEntity.ok(ApiResponse.success(null, "Triage review recorded successfully"));
    }
}
