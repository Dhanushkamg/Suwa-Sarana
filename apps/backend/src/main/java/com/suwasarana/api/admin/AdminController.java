package com.suwasarana.api.admin;

import com.suwasarana.api.admin.dto.DistrictSummaryDto;
import com.suwasarana.api.common.ApiResponse;
import com.suwasarana.api.request.BloodRequest;
import com.suwasarana.api.request.RequestReport;
import com.suwasarana.api.request.dto.RequestResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

    @Operation(summary = "Verify user account", description = "Manually marks a user account as verified.")
    @PostMapping("/requesters/{id}/verify")
    public ResponseEntity<ApiResponse<Void>> verifyUser(@PathVariable Long id) {
        adminService.verifyUser(id);
        return ResponseEntity.ok(ApiResponse.success(null, "User verified successfully"));
    }

    @Operation(summary = "Get flagged reports", description = "Retrieves abuse reports and flagged items submitted by community users.")
    @GetMapping("/reports")
    public ResponseEntity<ApiResponse<List<RequestReport>>> getReports() {
        return ResponseEntity.ok(ApiResponse.success(adminService.getReports()));
    }

    @Operation(summary = "Get platform analytics", description = "Returns key performance indicators and aggregate metrics.")
    @GetMapping("/analytics")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAnalytics() {
        return ResponseEntity.ok(ApiResponse.success(adminService.getDistrictAnalytics()));
    }

    @Operation(summary = "Get district summary analytics", description = "Aggregates donor counts, active blood requests, and fulfillment rates across all 25 Sri Lankan districts.")
    @GetMapping("/analytics/district-summary")
    public ResponseEntity<ApiResponse<List<DistrictSummaryDto>>> getDistrictSummary() {
        return ResponseEntity.ok(ApiResponse.success(adminService.getDistrictSummaries()));
    }

    @Operation(summary = "Get AI triage queue", description = "Retrieves active emergency requisitions prioritized by fraud risk score for moderation.")
    @GetMapping("/triage/queue")
    public ResponseEntity<ApiResponse<List<RequestResponseDto>>> getTriageQueue() {
        List<BloodRequest> queue = adminService.getTriageQueue();
        List<RequestResponseDto> dtos = queue.stream().map(this::mapToDto).toList();
        return ResponseEntity.ok(ApiResponse.success(dtos));
    }

    @Operation(summary = "Review triaged request", description = "Admin marks a triaged request as reviewed with optional audit notes.")
    @PutMapping("/triage/{id}/review")
    public ResponseEntity<ApiResponse<Void>> reviewTriage(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        String notes = body != null ? body.get("notes") : null;
        adminService.reviewTriageRequest(id, notes);
        return ResponseEntity.ok(ApiResponse.success(null, "Triage review recorded successfully"));
    }

    private RequestResponseDto mapToDto(BloodRequest request) {
        RequestResponseDto dto = new RequestResponseDto();
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
        return dto;
    }
}
