package com.suwasarana.api.analytics;

import com.suwasarana.api.analytics.dto.DistrictSummaryDto;
import com.suwasarana.api.analytics.dto.NationalSummaryDto;
import com.suwasarana.api.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Aggregates that are safe to show to any signed-in user: counts only, no admin-only breakdowns. */
@RestController
@RequestMapping("/api/analytics")
@Tag(name = "Analytics", description = "Shared analytics data accessible by authenticated users")
public class AnalyticsController {

    @Autowired
    private AnalyticsService analyticsService;

    @Operation(summary = "District summary (public view)",
            description = "One row for each of the 25 districts: donors, active requests and the 30-day fulfilment rate (null when nothing closed). Same numbers as the admin endpoint, without the admin-only fields.")
    @GetMapping("/district-summary")
    public ResponseEntity<ApiResponse<List<DistrictSummaryDto>>> getDistrictSummary() {
        return ResponseEntity.ok(ApiResponse.success(analyticsService.districtSummaries(false)));
    }

    @Operation(summary = "National KPIs (public view)",
            description = "Active requests, pooled fulfilment rate (null when nothing closed in the window), donors and districts with data.")
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<NationalSummaryDto>> getSummary() {
        return ResponseEntity.ok(ApiResponse.success(analyticsService.nationalSummary(false)));
    }
}
