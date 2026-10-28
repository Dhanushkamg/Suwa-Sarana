package com.suwasarana.api.analytics;

import com.suwasarana.api.analytics.dto.DistrictSummaryDto;
import com.suwasarana.api.analytics.dto.NationalSummaryDto;
import com.suwasarana.api.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Admin analytics: the same numbers as the shared endpoints, with the admin-only fields included. */
@RestController
@RequestMapping("/api/admin/analytics")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Analytics", description = "Administrative analytics computed in SQL")
public class AdminAnalyticsController {

    @Autowired
    private AnalyticsService analyticsService;

    @Operation(summary = "Active requests per district", description = "Map of district name to number of active requests (only districts that have any). Uses the same definition as the heatmap.")
    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Long>>> getAnalytics() {
        return ResponseEntity.ok(ApiResponse.success(analyticsService.activeRequestsByDistrict()));
    }

    @Operation(summary = "District summary (admin view)", description = "As the shared endpoint plus expired/cancelled counts and units outstanding.")
    @GetMapping("/district-summary")
    public ResponseEntity<ApiResponse<List<DistrictSummaryDto>>> getDistrictSummary() {
        return ResponseEntity.ok(ApiResponse.success(analyticsService.districtSummaries(true)));
    }

    @Operation(summary = "National KPIs (admin view)")
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<NationalSummaryDto>> getSummary() {
        return ResponseEntity.ok(ApiResponse.success(analyticsService.nationalSummary(true)));
    }
}
