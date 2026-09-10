package com.suwasarana.api.admin;

import com.suwasarana.api.admin.dto.DistrictSummaryDto;
import com.suwasarana.api.common.ApiResponse;
import com.suwasarana.api.request.BloodRequest;
import com.suwasarana.api.request.RequestReport;
import com.suwasarana.api.request.dto.RequestResponseDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    @Autowired
    private AdminService adminService;

    @PostMapping("/requesters/{id}/verify")
    public ResponseEntity<ApiResponse<Void>> verifyUser(@PathVariable Long id) {
        adminService.verifyUser(id);
        return ResponseEntity.ok(ApiResponse.success(null, "User verified successfully"));
    }

    @GetMapping("/reports")
    public ResponseEntity<ApiResponse<List<RequestReport>>> getReports() {
        return ResponseEntity.ok(ApiResponse.success(adminService.getReports()));
    }

    @GetMapping("/analytics")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAnalytics() {
        return ResponseEntity.ok(ApiResponse.success(adminService.getDistrictAnalytics()));
    }

    @GetMapping("/analytics/district-summary")
    public ResponseEntity<ApiResponse<List<DistrictSummaryDto>>> getDistrictSummary() {
        return ResponseEntity.ok(ApiResponse.success(adminService.getDistrictSummaries()));
    }

    @GetMapping("/triage/queue")
    public ResponseEntity<ApiResponse<List<RequestResponseDto>>> getTriageQueue() {
        List<BloodRequest> queue = adminService.getTriageQueue();
        List<RequestResponseDto> dtos = queue.stream().map(this::mapToDto).toList();
        return ResponseEntity.ok(ApiResponse.success(dtos));
    }

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
