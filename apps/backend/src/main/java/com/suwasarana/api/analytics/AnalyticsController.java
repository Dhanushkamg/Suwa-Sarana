package com.suwasarana.api.analytics;

import com.suwasarana.api.admin.AdminService;
import com.suwasarana.api.admin.dto.DistrictSummaryDto;
import com.suwasarana.api.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/analytics")
@Tag(name = "Analytics", description = "Shared analytics data accessible by authenticated users")
public class AnalyticsController {

    @Autowired
    private AdminService adminService;

    @Operation(summary = "Get district summary analytics", description = "Aggregates donor counts, active blood requests, and fulfillment rates across all 25 Sri Lankan districts. Accessible to all authenticated users.")
    @GetMapping("/district-summary")
    public ResponseEntity<ApiResponse<List<DistrictSummaryDto>>> getDistrictSummary() {
        return ResponseEntity.ok(ApiResponse.success(adminService.getDistrictSummaries()));
    }
}
